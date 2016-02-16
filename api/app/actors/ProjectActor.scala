package io.flow.delta.actors

import org.joda.time.DateTime
import io.flow.delta.api.lib.Semver
import io.flow.delta.aws.{AutoScalingGroup, EC2ContainerService, ElasticLoadBalancer}
import db.{TokensDao, UsersDao, ProjectLastStatesDao}
import io.flow.delta.api.lib.{GithubHelper, Repo}
import io.flow.delta.v0.models.{Project,StateForm}
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

    case object CheckLastState extends Message

    case object ConfigureEC2 extends Message // One-time EC2 setup
    case object ConfigureECS extends Message // One-time ECS setup

    case object CreateHooks extends Message
  }

}

class ProjectActor extends Actor with Util with DataProject with EventLog {

  override val logPrefix = "ProjectActor"

  implicit val projectActorExecutionContext: ExecutionContext = Akka.system.dispatchers.lookup("project-actor-context")

  def receive = {

    case msg @ ProjectActor.Messages.Data(id) => withVerboseErrorHandler(msg) {
      setDataProject(id)
    }

    case msg @ ProjectActor.Messages.CheckLastState => withVerboseErrorHandler(msg) {
      withProject { project =>
        withRepo { repo =>
          captureLastState(project, repo)
        }
      }
    }

    // Configure EC2 LC, ELB, ASG for a project (id: user, fulfillment, splashpage, etc)
    case msg @ ProjectActor.Messages.ConfigureEC2 => withVerboseErrorHandler(msg) {
      withProject { project =>
        val lc = createLaunchConfiguration(project)
        val elb = createLoadBalancer(project)
        createAutoScalingGroup(project, lc, elb)
      }
    }

    // Create ECS cluster for a project (id: user, fulfillment, splashpage, etc)
    case msg @ ProjectActor.Messages.ConfigureECS => withVerboseErrorHandler(msg) {
      withProject { project =>
        createCluster(project)
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

  def captureLastState(project: Project, repo: Repo) {
    // We want to get:
    //  0.0.1: 2 instances
    //  0.0.2: 1 instance
    log.run(s"Capturing last state for project ${project.name}") {
      ProjectLastStatesDao.upsert(
        UsersDao.systemUser,
        project,
        StateForm(versions = EC2ContainerService.getClusterInfo(project.id))
      )
    }
  }

  def createLaunchConfiguration(project: Project): String = {
    log.started("EC2 auto scaling group launch configuration")
    val lc = AutoScalingGroup.createLaunchConfiguration(project.id)
    log.completed(s"EC2 auto scaling group launch configuration: [$lc]")
    return lc
  }

  def createLoadBalancer(project: Project): String = {
    log.started("EC2 load balancer")
    val elb = ElasticLoadBalancer.createLoadBalancerAndHealthCheck(project.id)
    log.completed(s"EC2 Load Balancer: [$elb]")
    return elb
  }

  def createAutoScalingGroup(project: Project, launchConfigName: String, loadBalancerName: String) {
    log.started("EC2 auto scaling group")
    val asg = AutoScalingGroup.createAutoScalingGroup(project.id, launchConfigName, loadBalancerName)
    log.completed(s"EC2 auto scaling group: [$asg]")
  }

  def createCluster(project: Project) {
    log.started("ECS Cluster")
    val cluster = EC2ContainerService.createCluster(project.id)
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
