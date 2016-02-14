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

  def receive = {

    case m @ ProjectActor.Messages.Data(id) => withVerboseErrorHandler(m.toString) {
      setDataProject(id)
    }

    // Configure EC2 LC, ELB, ASG for a project (id: user, fulfillment, splashpage, etc)
    case msg @ ProjectActor.Messages.ConfigureEC2 => withVerboseErrorHandler(msg.toString) {
      withProject { project =>
        withRepo { repo =>
          val lc = createLaunchConfiguration(repo)
          val elb = createLoadBalancer(repo, project)
          createAutoScalingGroup(repo, lc, elb)
        }
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
          createHooks(project, repo)
        }
      }
    }

    case m: Any => logUnhandledMessage(m)

  }

  def createLaunchConfiguration(repo: Repo): String = {
    log.started("EC2 auto scaling group launch configuration")
    val lc = AutoScalingGroup.createLaunchConfiguration(repo.awsName)
    log.completed(s"EC2 auto scaling group launch configuration: [$lc]")
    return lc
  }

  def createLoadBalancer(repo: Repo, project: Project): String = {
    log.started("EC2 load balancer")
    val elb = ElasticLoadBalancer.createLoadBalancerAndHealthCheck(repo.awsName, project.name)
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
    log.completed(s"ECS Cluster: [$cluster]")
  }


  private[this] val HookBaseUrl = DefaultConfig.requiredString("delta.api.host") + "/webhooks/github/"
  private[this] val HookName = "web"
  private[this] val HookEvents = Seq(io.flow.github.v0.models.HookEvent.Push)

  private[this] def createHooks(project: Project, repo: Repo) {
    GithubHelper.apiClientFromUser(project.user.id) match {
      case None => {
        Logger.warn(s"Could not create github client for user[${project.user.id}]")
      }
      case Some(client) => {
        client.hooks.get(repo.owner, repo.project).map { hooks =>
          val targetUrl = HookBaseUrl + project.id

          hooks.find(_.config.url == Some(targetUrl)) match {
            case Some(hook) => {
              // No-op hook exists
            }
            case None => {
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
              Logger.info("Created githib webhook for project[${project.id}]: $hook")
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
