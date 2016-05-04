package io.flow.delta.actors

import com.amazonaws.services.ecs.model.Service
import db.{OrganizationsDao, TokensDao, UsersDao, BuildLastStatesWriteDao}
import io.flow.postgresql.Authorization
import io.flow.delta.aws.{AutoScalingGroup, EC2ContainerService, ElasticLoadBalancer, InstanceTypes, Settings}
import io.flow.delta.api.lib.{GithubHelper, RegistryClient, Repo, StateDiff}
import io.flow.delta.lib.{BuildNames, Semver, StateFormatter, Text}
import io.flow.delta.v0.models.{Build, Docker, StateForm}
import io.flow.play.actors.ErrorHandler
import io.flow.play.util.Config
import org.joda.time.DateTime
import play.api.Logger
import akka.actor.{Actor, ActorSystem}
import scala.util.{Failure, Success, Try}
import scala.concurrent.duration._
import scala.concurrent.Future

object BuildActor {

  val CheckLastStateIntervalSeconds = 45

  trait Message

  object Messages {
    case object CheckLastState extends Message

    case object ConfigureAWS extends Message // One-time AWS setup

    case class MonitorScale(imageName: String, imageVersion: String, start: DateTime) extends Message

    case class Scale(diffs: Seq[StateDiff]) extends Message

    case object Setup extends Message

    case object UpdateContainerAgent extends Message

    case object RemoveOldServices extends Message
  }

  trait Factory {
    def apply(buildId: String): Actor
  }

}

class BuildActor @javax.inject.Inject() (
  registryClient: RegistryClient,
  config: Config,
  system: ActorSystem,
  @com.google.inject.assistedinject.Assisted buildId: String
) extends Actor with ErrorHandler with DataBuild with BuildEventLog {

  implicit private[this] val ec = system.dispatchers.lookup("build-actor-context")

  private[this] val TimeoutSeconds = 450
  private[this] lazy val awsSettings = withBuild { build =>
    awsSettingsForBuild(build)
  }.getOrElse {
    sys.error("Must have build before getting settings for auto scaling group")
  }

  private[this] lazy val ecs = EC2ContainerService(awsSettings, registryClient)
  private[this] lazy val elb = ElasticLoadBalancer(awsSettings, registryClient)
  private[this] lazy val asg = AutoScalingGroup(
    awsSettings,
    ecs,
    dockerHubToken = config.requiredString("dockerhub.delta.auth.token"),
    dockerHubEmail = config.requiredString("dockerhub.delta.auth.email")
  )

  private[this] def awsSettingsForBuild(build: Build): Settings = {
    // TODO: Find place for this configuration. Probably in UI
    build.project.name == "classification" match {
      case false => InstanceTypes.T2Micro
      case true => InstanceTypes.T2Medium
    }
  }

  def receive = {
    // case msg @ BuildActor.Messages.Data(id) => withVerboseErrorHandler(msg) {
    case msg @ BuildActor.Messages.Setup => withVerboseErrorHandler(msg) {
      setBuildId(buildId)

      if (isScaleEnabled) {
        self ! BuildActor.Messages.ConfigureAWS

        system.scheduler.schedule(
          Duration(1, "second"),
          Duration(BuildActor.CheckLastStateIntervalSeconds, "seconds")
        ) {
          self ! BuildActor.Messages.CheckLastState
        }
      }
    }

    case msg @ BuildActor.Messages.CheckLastState => withVerboseErrorHandler(msg) {
      withBuild { build =>
        captureLastState(build)
      }
    }

    case msg @ BuildActor.Messages.UpdateContainerAgent => withVerboseErrorHandler(msg) {
      withBuild { build =>
        updateContainerAgent(build)
      }
    }

    case msg @ BuildActor.Messages.RemoveOldServices => withVerboseErrorHandler(msg) {
      withBuild { build =>
        removeOldServices(build)
      }
    }

    // Configure EC2 LC, ELB, ASG for a build (id: user, fulfillment, splashpage, etc)
    case msg @ BuildActor.Messages.ConfigureAWS => withVerboseErrorHandler(msg) {
      withBuild { build =>
        configureAWS(build)
      }
    }

    case msg @ BuildActor.Messages.Scale(diffs) => withVerboseErrorHandler(msg) {
      withOrganization { org =>
        withBuild { build =>
          diffs.foreach { diff =>
            scale(org.docker, build, diff)
          }
        }
      }
    }

    case msg @ BuildActor.Messages.MonitorScale(imageName, imageVersion, start) => withVerboseErrorHandler(msg) {
      withBuild { build =>
        monitorScale(build, imageName, imageVersion, start)
      }
    }

    case msg: Any => logUnhandledMessage(msg)

  }

  private[this] def isScaleEnabled(): Boolean = {
    withSettings { _.scale }.getOrElse(false)
  }

  def configureAWS(build: Build): Future[Unit] = {
    log.runAsync("configureAWS") {
      for {
        cluster <- createCluster(build)
        lc <- createLaunchConfiguration(build)
        elb <- createLoadBalancer(build)
        asg <- createAutoScalingGroup(build, lc, elb)
      } yield {
        // All steps have completed
      }
    }
  }

  def updateContainerAgent(build: Build) {
    log.runAsync("ECS updating container agent") {
      ecs.updateContainerAgent(BuildNames.projectName(build))
    }
  }

  def removeOldServices(build: Build): Unit = {
    log.runAsync("ECS cleanup old services") {
      ecs.removeOldServices(BuildNames.projectName(build))
    }
  }

  def scale(docker: Docker, build: Build, diff: StateDiff) {
    val projectName = BuildNames.projectName(build)
    val imageName = BuildNames.dockerImageName(docker, build)
    val imageVersion = diff.versionName

    if (diff.lastInstances > diff.desiredInstances) {
      val instances = diff.lastInstances - diff.desiredInstances
      log.runAsync(s"Bring down ${Text.pluralize(instances, "instance", "instances")} of ${diff.versionName}") {
        ecs.scale(imageName, imageVersion, projectName, diff.desiredInstances)
      }

    } else if (diff.lastInstances < diff.desiredInstances) {
      val instances = diff.desiredInstances - diff.lastInstances
      log.runAsync(s"Bring up ${Text.pluralize(instances, "instance", "instances")} of ${diff.versionName}") {
        ecs.scale(imageName, imageVersion, projectName, diff.desiredInstances)
      }
    }

    monitorScale(build, imageName, imageVersion, new DateTime())
  }

  def monitorScale(build: Build, imageName: String, imageVersion: String, start: DateTime) {
    captureLastState(build)

    for {
      ecsServiceOpt <- getServiceInfo(imageName, imageVersion, build)
      isHealthy <- isServiceHealthy(imageName, imageVersion, build)
    } yield {
      ecsServiceOpt match {
        case None => {
          sys.error(s"ECS Service not found for build $build.id, image $imageName, version $imageVersion")
        }

        case Some(service) => {
          val summary = ecs.summary(service)
          val intervalSeconds = 5

          if (service.getRunningCount == service.getDesiredCount) {
            if (isHealthy) {
              log.completed(s"${imageName}:${imageVersion} $summary")
            } else {
              log.checkpoint(s"${imageName}:${imageVersion} running, but waiting for ELB instances to become healthy. Will recheck in $intervalSeconds seconds. $summary")

              system.scheduler.scheduleOnce(Duration(intervalSeconds, "seconds")) {
                self ! BuildActor.Messages.MonitorScale(imageName, imageVersion, start)
              }
            }

          } else if (start.plusSeconds(TimeoutSeconds).isBefore(new DateTime)) {
            log.error(s"Timeout after $TimeoutSeconds seconds. Failed to scale ${imageName}:${imageVersion}. $summary")

          } else {
            log.checkpoint(s"Waiting for ${imageName}:${imageVersion}. Will recheck in $intervalSeconds seconds. $summary")

            system.scheduler.scheduleOnce(Duration(intervalSeconds, "seconds")) {
              self ! BuildActor.Messages.MonitorScale(imageName, imageVersion, start)
            }
          }
        }
      }
    }
  }

  def isServiceHealthy(imageName: String, imageVersion: String, build: Build): Future[Boolean] = {
    log.runAsync(s"Check service health of image: ${imageName}, version: ${imageVersion}") {
      for {
        serviceInstances <- ecs.getServiceInstances(imageName, imageVersion, BuildNames.projectName(build))
        healthyInstances <- elb.getHealthyInstances(BuildNames.projectName(build))
      } yield {
        val result = !serviceInstances.isEmpty && serviceInstances.forall(healthyInstances.contains(_))
        val projectName = BuildNames.projectName(build)
        Logger.info(s"isServiceHealthy($imageName, $imageVersion, $projectName ==> $result")
        Logger.info(s"  - $projectName: healthyInstances: ${healthyInstances.sorted}")
        Logger.info(s"  - $projectName: serviceInstances: ${serviceInstances.sorted}")
        result
      }
    }
  }

  def captureLastState(build: Build): Future[String] = {
    ecs.getClusterInfo(BuildNames.projectName(build)).map { versions =>
      play.api.Play.current.injector.instanceOf[BuildLastStatesWriteDao].upsert(
        UsersDao.systemUser,
        build,
        StateForm(versions = versions)
      )
      StateFormatter.label(versions)
    }
  }

  def getServiceInfo(imageName: String, imageVersion: String, build: Build): Future[Option[Service]] = {
    log.runSync("Getting ECS service Info", quiet = true) {
      ecs.getServiceInfo(imageName, imageVersion, BuildNames.projectName(build))
    }
  }

  def createLaunchConfiguration(build: Build): Future[String] = {
    log.runSync("EC2 auto scaling group launch configuration") {
      asg.createLaunchConfiguration(BuildNames.projectName(build))
    }
  }

  def createLoadBalancer(build: Build): Future[String] = {
    log.runAsync("EC2 load balancer") {
      elb.createLoadBalancerAndHealthCheck(BuildNames.projectName(build))
    }
  }

  def createAutoScalingGroup(build: Build, launchConfigName: String, loadBalancerName: String): Future[String] = {
    log.runSync("EC2 auto scaling group") {
      asg.createAutoScalingGroup(BuildNames.projectName(build), launchConfigName, loadBalancerName)
    }
  }

  def createCluster(build: Build): Future[String] = {
    log.runSync("Create cluster") {
       ecs.createCluster(BuildNames.projectName(build))
    }
  }

}
