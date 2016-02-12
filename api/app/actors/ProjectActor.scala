package io.flow.delta.actors

import aws._
import io.flow.postgresql.Authorization
import db.{ProjectsDao, TokensDao, UsersDao}
import io.flow.delta.api.lib.{GithubUtil, GithubHelper}
import io.flow.delta.v0.models.Project
import io.flow.play.actors.Util
import io.flow.play.util.DefaultConfig
import play.api.Logger
import play.libs.Akka
import akka.actor.Actor
import scala.concurrent.ExecutionContext

//import play.api.Play.current

object ProjectActor {

  trait Message

  object Messages {
    case class Data(id: String)

    case object ConfigureEC2 // One-time EC2 setup
    case object ConfigureECS // One-time ECS setup

    case object CreateHooks extends Message
    case object Sync extends Message
  }

}

class ProjectActor extends Actor with Util {

  implicit val projectActorExecutionContext: ExecutionContext = Akka.system.dispatchers.lookup("project-actor-context")

  private[this] val HookBaseUrl = DefaultConfig.requiredString("delta.api.host") + "/webhooks/github/"
  private[this] val HookName = "web"
  private[this] val HookEvents = Seq(io.flow.github.v0.models.HookEvent.Push)

  private[this] var dataProject: Option[Project] = None
  
  def receive = {

    case m @ ProjectActor.Messages.Data(id) => withVerboseErrorHandler(m.toString) {
      dataProject = ProjectsDao.findById(Authorization.All, id)
    }
    
    // Configure EC2 LC, ELB, ASG for a project (id: user, fulfillment, splashpage, etc)
    case msg @ ProjectActor.Messages.ConfigureEC2 => withVerboseErrorHandler(msg.toString) {
      dataProject.foreach { project =>
        val lc = createLaunchConfiguration(project.id)
        val elb = createLoadBalancer(project.id)
        createAutoScalingGroup(project.id, lc, elb)
      }
    }

    // Create ECS cluster for a project (id: user, fulfillment, splashpage, etc)
    case msg @ ProjectActor.Messages.ConfigureECS => withVerboseErrorHandler(msg.toString) {
      dataProject.foreach { project =>
        createCluster(project.id)
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

    case m @ ProjectActor.Messages.Sync => withVerboseErrorHandler(m.toString) {
      dataProject.foreach { project =>
        println(s"ProjectActor.Messages.Sync id[${project.id}] name[${project.name}]")

        UsersDao.findById(project.user.id).flatMap { u =>
          TokensDao.getCleartextGithubOauthTokenByUserId(u.id)
        } match {
          case None => {
            Logger.warn(s"No oauth token for project[${project.id}] user[${project.user.id}]")
          }
          case Some(token) => {
            GithubUtil.parseUri(project.uri) match {
              case Left(errors) => {
                Logger.warn(s"Error parsing project name[${project.name}]: $errors")
              }
              case Right(repo) => {
                val client = GithubHelper.apiClient(token)

                for {
                  master <- client.refs.getByRef(repo.owner, repo.project, "heads/master")
                  tags <- client.tags.get(repo.owner, repo.project)
                } yield {
                  val masterSha = master.`object`.sha

                  tags.find { t => t.commit.sha == masterSha } match {
                    case None => println("  No tag found matching master[$masterSha]")
                    case Some(t) => println(s"  Tag[${t.name}] is master[$masterSha]")
                  }

                  println("")
                  println("  ALL TAGS")
                  tags.foreach { tag =>
                    println(s"   - ${tag.name}[${tag.commit.sha}]")
                  }
                }
              }
            }
          }
        }
      }
    }

    case m: Any => logUnhandledMessage(m)

  }

  def createLaunchConfiguration(id: String): String = {
    val lc = AutoScalingGroup.createLaunchConfiguration(id)
    println(s"[ProjectActor.Messages.ConfigureEC2] Done - Project: [$id], EC2 Launch Configuration: [${lc}]")
    return lc
  }

  def createLoadBalancer(id: String): String = {
    val elb = ElasticLoadBalancer.createLoadBalancerAndHealthCheck(id)
    println(s"[ProjectActor.Messages.ConfigureEC2] Done - Project: [$id], EC2 Load Balancer: [${elb}]")
    return elb
  }

  def createAutoScalingGroup(id: String, launchConfigName: String, loadBalancerName: String) {
    val asg = AutoScalingGroup.createAutoScalingGroup(id, launchConfigName, loadBalancerName)
    println(s"[ProjectActor.Messages.ConfigureEC2] Done - Project: [$id], EC2 Auto-Scaling Group: [${asg}]")
  }

  def createCluster(id: String) {
    val cluster = EC2ContainerService.createCluster(id)
    println(s"[ProjectActor.Messages.ConfigureECS] Done - Project: [$id], ECS Cluster: [${cluster}]")
  }

}
