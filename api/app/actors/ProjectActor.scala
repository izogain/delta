package io.flow.delta.actors

import io.flow.postgresql.Authorization
import org.joda.time.DateTime
import io.flow.delta.api.lib.Semver
import io.flow.delta.aws.{AutoScalingGroup, EC2ContainerService, ElasticLoadBalancer}
import db.{OrganizationsDao, TokensDao, UsersDao, ProjectLastStatesDao}
import io.flow.delta.api.lib.{GithubHelper, Repo, StateDiff}
import io.flow.delta.v0.models.{Project, StateForm}
import io.flow.delta.lib.Text
import io.flow.play.actors.Util
import io.flow.play.util.DefaultConfig
import play.api.Logger
import play.libs.Akka
import akka.actor.Actor
import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success, Try}
import scala.concurrent.duration._

object ProjectActor {

  trait Message

  object Messages {
    case class Data(id: String) extends Message

    case object CheckLastState extends Message

    case object ConfigureEC2 extends Message // One-time EC2 setup
    case object ConfigureECS extends Message // One-time ECS setup

    case object CreateHooks extends Message

    case class Scale(diffs: Seq[StateDiff]) extends Message
    case class MonitorScale(imageName: String, imageVersion: String) extends Message
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
        captureLastState(project)
      }
    }

    // Configure EC2 LC, ELB, ASG for a project (id: user, fulfillment, splashpage, etc)
    case msg @ ProjectActor.Messages.ConfigureEC2 => withVerboseErrorHandler(msg) {
      withProject { project =>
        Try(
          configureEc2(project)
        ) match {
          case Success(_) => // do nothing
          case Failure(e) => log.completed("Error configuring EC2", Some(e))
        }
      }
    }

    // Create ECS cluster for a project (id: user, fulfillment, splashpage, etc)
    case msg @ ProjectActor.Messages.ConfigureECS => withVerboseErrorHandler(msg) {
      withProject { project =>
        Try(
          configureEcs(project)
        ) match {
          case Success(_) => // do nothing
          case Failure(e) => log.completed("Error configuring EC2", Some(e))
        }
      }
    }

    case msg @ ProjectActor.Messages.CreateHooks => withVerboseErrorHandler(msg) {
      withProject { project =>
        withRepo { repo =>
          createHooks(project, repo)
        }
      }
    }

    case msg @ ProjectActor.Messages.Scale(diffs) => withVerboseErrorHandler(msg) {
      withProject { project =>
        diffs.foreach { diff =>
          Try(
            scale(project, diff)
          ) match {
            case Success(_) => // do nothing
            case Failure(e) => log.completed("Scale attempt ended with failure", Some(e))
          }
        }
      }
    }

    case msg @ ProjectActor.Messages.MonitorScale(imageName, imageVersion) => withVerboseErrorHandler(msg) {
      withProject { project =>
        Try(
          monitorScale(project, imageName, imageVersion)
        ) match {
          case Success(_) => // do nothing
          case Failure(e) => log.completed("Monitor Scale attempt ended with failure", Some(e))
        }
      }
    }

    case msg: Any => logUnhandledMessage(msg)

  }

  def configureEc2(project: Project) {
    log.run(s"Configuring EC2") {
      val lc = createLaunchConfiguration(project)
      val elb = createLoadBalancer(project)
      createAutoScalingGroup(project, lc, elb)
    }
  }

  def configureEcs(project: Project) {
    log.run(s"Configuring ECS") {
      // Only one thing for now, but room to add more stuff later...
      createCluster(project)
    }
  }

  def scale(project: Project, diff: StateDiff) {
    val org = OrganizationsDao.findById(Authorization.All, project.organization.id).get
    val imageName = s"${org.docker.organization}/${project.id}"
    val imageVersion = diff.versionName

    if (diff.lastInstances > diff.desiredInstances) {
      val instances = diff.lastInstances - diff.desiredInstances
      log.run(s"Bring down ${Text.pluralize(instances, "instance", "instances")} of ${diff.versionName}") {
        EC2ContainerService.scale(imageName, imageVersion, project.id, diff.desiredInstances)
      }
    } else if (diff.lastInstances < diff.desiredInstances) {
      val instances = diff.desiredInstances - diff.lastInstances
      log.run(s"Bring up ${Text.pluralize(instances, "instance", "instances")} of ${diff.versionName}") {
        EC2ContainerService.scale(imageName, imageVersion, project.id, diff.desiredInstances)
      }
    }

    monitorScale(project, imageName, imageVersion)
  }

  def monitorScale(project: Project, imageName: String, imageVersion: String) {
    val ecsService = EC2ContainerService.getServiceInfo(imageName, imageVersion, project.id)
    val running = ecsService.getRunningCount
    val desired = ecsService.getDesiredCount
    val pending = ecsService.getPendingCount
    val status = ecsService.getStatus

    if (running == desired) {
      log.completed(s"Scaling ${imageName}, Version: ${imageVersion}, Running: $running, Pending: $pending, Desired: $desired.")
    } else {
      log.checkpoint(s"Scaling ${imageName}, Version: ${imageVersion}, Running: $running, Pending: $pending, Desired: $desired. Next update in ~5 seconds.")

      Akka.system.scheduler.scheduleOnce(Duration(5, "seconds")) {
        self ! ProjectActor.Messages.MonitorScale(imageName, imageVersion)
      }
    }
  }

  def captureLastState(project: Project) {
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
