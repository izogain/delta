package io.flow.delta.actors

import com.amazonaws.services.ecs.model.Service
import io.flow.postgresql.Authorization
import org.joda.time.DateTime
import io.flow.delta.api.lib.{Semver, StateFormatter}
import io.flow.delta.aws.{AutoScalingGroup, EC2ContainerService, ElasticLoadBalancer}
import db.{OrganizationsDao, TokensDao, UsersDao, ProjectLastStatesDao}
import io.flow.delta.api.lib.{GithubHelper, RegistryClient, Repo, StateDiff}
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
import scala.concurrent.Future

object ProjectActor {

  val CheckLastStateIntervalSeconds = 45

  trait Message

  object Messages {
    case object CheckLastState extends Message

    case object ConfigureAWS extends Message // One-time AWS setup

    case object CreateHooks extends Message

    case class MonitorScale(imageName: String, imageVersion: String) extends Message

    case class Scale(diffs: Seq[StateDiff]) extends Message

    case object Setup extends Message    
  }

  trait Factory {
    def apply(projectId: String): Actor
  }

}

class ProjectActor @javax.inject.Inject() (
  registryClient: RegistryClient,
  @com.google.inject.assistedinject.Assisted projectId: String
) extends Actor with Util with DataProject with EventLog {

  override val logPrefix = s"ProjectActor"

  implicit val projectActorExecutionContext: ExecutionContext = Akka.system.dispatchers.lookup("project-actor-context")

  private[this] lazy val ecs = EC2ContainerService
  private[this] lazy val elb = ElasticLoadBalancer
  private[this] lazy val asg = AutoScalingGroup

  def receive = {

    // case msg @ ProjectActor.Messages.Data(id) => withVerboseErrorHandler(msg) {
    case msg @ ProjectActor.Messages.Setup => withVerboseErrorHandler(msg) {
      setProjectId(projectId)

      // Verify hooks, AWS have been setup
      self ! ProjectActor.Messages.CreateHooks

      if (isScaleEnabled) {
        self ! ProjectActor.Messages.ConfigureAWS

        withProject { project =>
          Akka.system.scheduler.schedule(
            Duration(1, "second"),
            Duration(ProjectActor.CheckLastStateIntervalSeconds, "seconds")
          ) {
            self ! ProjectActor.Messages.CheckLastState
          }
        }
      }
    }

    case msg @ ProjectActor.Messages.CheckLastState => withVerboseErrorHandler(msg) {
      withProject { project =>
        captureLastState(project)
      }
    }

    // Configure EC2 LC, ELB, ASG for a project (id: user, fulfillment, splashpage, etc)
    case msg @ ProjectActor.Messages.ConfigureAWS => withVerboseErrorHandler(msg) {
      withProject { project =>
        configureAWS(project)
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
          scale(project, diff)
        }
      }
    }

    case msg @ ProjectActor.Messages.MonitorScale(imageName, imageVersion) => withVerboseErrorHandler(msg) {
      withProject { project =>
        monitorScale(project, imageName, imageVersion)
      }
    }

    case msg: Any => logUnhandledMessage(msg)

  }

  private[this] def isScaleEnabled(): Boolean = {
    withSettings { _.scale }.getOrElse(false)
  }

  def configureAWS(project: Project): Future[Unit] = {
    log.runAsync("configureAWS") {
      for {
        cluster <- createCluster(project)
        lc <- createLaunchConfiguration(project)
        elb <- createLoadBalancer(project)
        asg <- createAutoScalingGroup(project, lc, elb)
      } yield {
        // All steps have completed
      }
    }
  }

  def scale(project: Project, diff: StateDiff) {
    val org = OrganizationsDao.findById(Authorization.All, project.organization.id).get
    val imageName = s"${org.docker.organization}/${project.id}"
    val imageVersion = diff.versionName

    if (diff.lastInstances > diff.desiredInstances) {
      val instances = diff.lastInstances - diff.desiredInstances
      log.runSync(s"Bring down ${Text.pluralize(instances, "instance", "instances")} of ${diff.versionName}") {
        ecs.scale(imageName, imageVersion, project.id, diff.desiredInstances)
      }

    } else if (diff.lastInstances < diff.desiredInstances) {
      val instances = diff.desiredInstances - diff.lastInstances
      log.runSync(s"Bring up ${Text.pluralize(instances, "instance", "instances")} of ${diff.versionName}") {
        ecs.scale(imageName, imageVersion, project.id, diff.desiredInstances)
      }
    }

    monitorScale(project, imageName, imageVersion)
  }

  def monitorScale(project: Project, imageName: String, imageVersion: String) {
    captureLastState(project)

    for {
      ecsServiceOpt <- getServiceInfo(imageName, imageVersion, project)
    } yield {
      ecsServiceOpt match {
        case None => {
          sys.error(s"ECS Service not found for project $project.id, image $imageName, version $imageVersion")
        }

        case Some(ecsService) => {
          val running = ecsService.getRunningCount
          val desired = ecsService.getDesiredCount
          val pending = ecsService.getPendingCount
          val status = ecsService.getStatus
          val intervalSeconds = 5

          if (running == desired) {
            log.checkpoint(s"Scaling ${imageName}, Version: ${imageVersion}, Running: $running, Pending: $pending, Desired: $desired.")
          } else {
            log.checkpoint(s"Scaling ${imageName}, Version: ${imageVersion}, Running: $running, Pending: $pending, Desired: $desired. Next update in ~$intervalSeconds seconds.")

            Akka.system.scheduler.scheduleOnce(Duration(intervalSeconds, "seconds")) {
              self ! ProjectActor.Messages.MonitorScale(imageName, imageVersion)
            }
          }
        }
      }
    }
  }

  def captureLastState(project: Project): Future[String] = {
    log.runAsync("captureLastState") {
      ecs.getClusterInfo(project.id).map { versions =>
        ProjectLastStatesDao.upsert(
          UsersDao.systemUser,
          project,
          StateForm(versions = versions)
        )
        StateFormatter.label(versions)
      }
    }
  }

  def getServiceInfo(imageName: String, imageVersion: String, project: Project): Future[Option[Service]] = {
    log.runSync("Getting ECS service Info") {
      ecs.getServiceInfo(imageName, imageVersion, project.id)
    }
  }

  def createLaunchConfiguration(project: Project): Future[String] = {
    log.runSync("EC2 auto scaling group launch configuration") {
      asg.createLaunchConfiguration(project.id)
    }
  }

  def createLoadBalancer(project: Project): Future[String] = {
    log.runAsync("EC2 load balancer") {
      elb.createLoadBalancerAndHealthCheck(project.id)
    }
  }

  def createAutoScalingGroup(project: Project, launchConfigName: String, loadBalancerName: String): Future[String] = {
    log.runSync("EC2 auto scaling group") {
      asg.createAutoScalingGroup(project.id, launchConfigName, loadBalancerName)
    }
  }

  def createCluster(project: Project): Future[String] = {
    log.runSync("Create cluster") {
       ecs.createCluster(project.id)
    }
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
