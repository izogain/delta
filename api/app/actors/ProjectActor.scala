package io.flow.delta.actors

import aws._
import io.flow.postgresql.Authorization
import db.{ProjectsDao, ShasDao, TokensDao, UsersDao}
import io.flow.delta.api.lib.{EventLog, GithubUtil, GithubHelper, Repo}
import io.flow.delta.v0.models.Project
import io.flow.play.actors.Util
import io.flow.play.util.DefaultConfig
import play.api.Logger
import play.libs.Akka
import akka.actor.Actor
import scala.concurrent.ExecutionContext

object ProjectActor {

  trait Message

  object Messages {
    case class Data(id: String) extends Message

    case object ConfigureEC2 extends Message // One-time EC2 setup
    case object ConfigureECS extends Message // One-time ECS setup

    case object CreateHooks extends Message
    case object SyncGithub extends Message
  }

}

class ProjectActor extends Actor with Util {

  implicit val projectActorExecutionContext: ExecutionContext = Akka.system.dispatchers.lookup("project-actor-context")

  private[this] val HookBaseUrl = DefaultConfig.requiredString("delta.api.host") + "/webhooks/github/"
  private[this] val HookName = "web"
  private[this] val HookEvents = Seq(io.flow.github.v0.models.HookEvent.Push)

  private[this] var dataProject: Option[Project] = None
  private[this] var dataRepo: Option[Repo] = None

  private[this] def log: EventLog = {
    dataProject.map { EventLog.withSystemUser(_, "ProjectActor") }.getOrElse {
      sys.error("Cannot get log with empty data")
    }
  }
  
  def receive = {

    case m @ ProjectActor.Messages.Data(id) => withVerboseErrorHandler(m.toString) {
      ProjectsDao.findById(Authorization.All, id) match {
        case None => {
          dataProject = None
          dataRepo = None
        }
        case Some(project) => {
          dataProject = Some(project)
          dataRepo = GithubUtil.parseUri(project.uri) match {
            case Left(error) => {
              Logger.warn(s"Project id[${project.id}] name[${project.name}]: $error")
              None
            }
            case Right(repo) => {
              Some(repo)
            }
          }
        }
      }
    }
    
    // Configure EC2 LC, ELB, ASG for a project (id: user, fulfillment, splashpage, etc)
    case msg @ ProjectActor.Messages.ConfigureEC2 => withVerboseErrorHandler(msg.toString) {
      dataRepo.foreach { repo =>
        val lc = createLaunchConfiguration(repo)
        val elb = createLoadBalancer(repo)
        createAutoScalingGroup(repo, lc, elb)
      }
    }

    // Create ECS cluster for a project (id: user, fulfillment, splashpage, etc)
    case msg @ ProjectActor.Messages.ConfigureECS => withVerboseErrorHandler(msg.toString) {
      dataRepo.foreach { repo =>
        createCluster(repo)
      }
    }

    case m @ ProjectActor.Messages.CreateHooks => withVerboseErrorHandler(m.toString) {
      dataProject.foreach { project =>
        GithubUtil.parseUri(project.uri) match {
          case Left(error) => {
            Logger.warn(s"Project id[${project.id}] name[${project.name}]: $error")
          }
          case Right(repo) => {
            println(s"Create Hooks for project[${project.id}] repo[$repo]")
            UsersDao.findById(project.user.id).flatMap { u =>
              TokensDao.getCleartextGithubOauthTokenByUserId(u.id)
            } match {
              case None => {
                Logger.warn(s"No oauth token for user[${project.user.id}]")
              }
              case Some(token) => {
                println(s"Create Hooks for project[${project.id}] user[${project.user.id}] token[$token]")
                val client = GithubHelper.apiClient(token)

                client.hooks.get(repo.owner, repo.project).map { hooks =>
                  val targetUrl = HookBaseUrl + project.id
                  println(s"Got back from call to get targetUrl[$targetUrl]")

                  hooks.foreach { hook =>
                    println(s"hook id[${hook.id}] url[${hook.url}]")
                  }
                  hooks.find(_.config.url == Some(targetUrl)) match {
                    case Some(hook) => {
                      println("  - existing hook found: " + hook.id)
                      println("  - existing hook events: " + hook.events)
                    }
                    case None => {
                      println("  - hook not found. Creating")
                      println(s"  - HookEvents: ${HookEvents}")
                      client.hooks.post(
                        owner = repo.owner,
                        repo = repo.project,
                        name = HookName,
                        config = io.flow.github.v0.models.HookConfig(
                          url = Some(targetUrl),
                          contentType = Some("json")
                        ),
                        events = HookEvents,
                        active = true
                      )
                    }.map { hook =>
                      println("  - hook created: " + hook)
                    }.recover {
                      case e: Throwable => {
                        Logger.error("Project[${project.id}] Error creating hook: " + e)
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
    }

    /**
      * Look up the sha for the master branch from github, and record
      * it in the shas table.
      */
    case m @ ProjectActor.Messages.SyncGithub => withVerboseErrorHandler(m.toString) {
      dataProject.foreach { project =>
        dataRepo.foreach { repo =>
          GithubHelper.apiClientFromUser(project.user.id).map { client =>
            client.refs.getByRef(repo.owner, repo.project, "heads/master").map { master =>
              ShasDao.upsertMaster(UsersDao.systemUser, project.id, master.`object`.sha)
            }
          }
        }
      }
    }

    case m: Any => logUnhandledMessage(m)

  }

  def createLaunchConfiguration(repo: Repo): String = {
    log.started("EC2 auto scaling group launch configuration")
    val lc = AutoScalingGroup.createLaunchConfiguration(repo.awsName)
    log.completed("EC2 auto scaling group launch configuration: [$lc]")
    return lc
  }

  def createLoadBalancer(repo: Repo): String = {
    log.started("EC2 load balancer")
    val elb = ElasticLoadBalancer.createLoadBalancerAndHealthCheck(repo.awsName)
    log.completed(s"EC2 Load Balancer: [$elb]")
    return elb
  }

  def createAutoScalingGroup(repo: Repo, launchConfigName: String, loadBalancerName: String) {
    log.started("EC2 auto scaling group")
    val asg = AutoScalingGroup.createAutoScalingGroup(repo.awsName, launchConfigName, loadBalancerName)
    log.completed(s"EC2 auto scaling group: [$asg]")
  }

  def createCluster(repo: Repo) {
    log.started("ECS Cluster")
    val cluster = EC2ContainerService.createCluster(repo.awsName)
    log.completed("ECS Cluster: [$cluster]")
  }

}
