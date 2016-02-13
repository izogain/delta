package io.flow.delta.actors

import io.flow.delta.api.lib.Semver
import io.flow.delta.aws.{AutoScalingGroup, EC2ContainerService, ElasticLoadBalancer}
import db.{TokensDao, UsersDao}
import io.flow.delta.api.lib.{GithubHelper, Repo}
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
  }

}

class ProjectActor extends Actor with Util with DataProject with EventLog {

  override val logPrefix = "ProjectActor"

  implicit val projectActorExecutionContext: ExecutionContext = Akka.system.dispatchers.lookup("project-actor-context")

  private[this] val HookBaseUrl = DefaultConfig.requiredString("delta.api.host") + "/webhooks/github/"
  private[this] val HookName = "web"
  private[this] val HookEvents = Seq(io.flow.github.v0.models.HookEvent.Push)

  def receive = {

    case m @ ProjectActor.Messages.Data(id) => withVerboseErrorHandler(m.toString) {
      setDataProject(id)
    }

    // Configure EC2 LC, ELB, ASG for a project (id: user, fulfillment, splashpage, etc)
    case msg @ ProjectActor.Messages.ConfigureEC2 => withVerboseErrorHandler(msg.toString) {
      withRepo { repo =>
        val lc = createLaunchConfiguration(repo)
        val elb = createLoadBalancer(repo)
        createAutoScalingGroup(repo, lc, elb)
      }
    }

    // Create ECS cluster for a project (id: user, fulfillment, splashpage, etc)
    case msg @ ProjectActor.Messages.ConfigureECS => withVerboseErrorHandler(msg.toString) {
      withRepo { repo =>
        createCluster(repo)
      }
    }

    case m @ ProjectActor.Messages.CreateHooks => withVerboseErrorHandler(m.toString) {
      withProject { project =>
        withRepo { repo =>
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
