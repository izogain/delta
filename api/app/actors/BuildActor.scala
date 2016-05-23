package io.flow.delta.actors

import com.amazonaws.services.ecs.model.Service
import db.{ConfigsDao, OrganizationsDao, TokensDao, UsersDao, BuildLastStatesWriteDao}
import io.flow.postgresql.Authorization
import io.flow.delta.aws.{AutoScalingGroup, DefaultSettings, EC2ContainerService, ElasticLoadBalancer, InstanceTypeDefaults}
import io.flow.delta.api.lib.{GithubHelper, RegistryClient, Repo, StateDiff}
import io.flow.delta.lib.{BuildNames, Semver, StateFormatter, Text}
import io.flow.delta.v0.models.{Build, Docker, StateForm}
import io.flow.delta.config.v0.models.BuildStage
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
  val ScaleIntervalSeconds = 5

  trait Message

  object Messages {
    case object CheckLastState extends Message

    case object ConfigureAWS extends Message // One-time AWS setup

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
  buildLastStatesWriteDao: BuildLastStatesWriteDao,
  override val configsDao: ConfigsDao,
  asg: AutoScalingGroup,
  ecs: EC2ContainerService,
  elb: ElasticLoadBalancer,
  @com.google.inject.assistedinject.Assisted buildId: String
) extends Actor with ErrorHandler with DataBuild with BuildEventLog {

  implicit private[this] val ec = system.dispatchers.lookup("build-actor-context")

  private[this] val TimeoutSeconds = 450
  private[this] lazy val awsSettings = withBuildConfig { bc =>
    DefaultSettings(
      instanceType = bc.instanceType,
      containerMemory = InstanceTypeDefaults.containerMemory(bc.instanceType)
    )
  }.getOrElse {
    sys.error("Must have build configuration before getting settings for auto scaling group")
  }

  def receive = {

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
      withEnabledBuild { build =>
        captureLastState(build)
      }
    }

    case msg @ BuildActor.Messages.UpdateContainerAgent => withVerboseErrorHandler(msg) {
      withEnabledBuild { build =>
        updateContainerAgent(build)
      }
    }

    case msg @ BuildActor.Messages.RemoveOldServices => withVerboseErrorHandler(msg) {
      withEnabledBuild { build =>
        removeOldServices(build)
      }
    }

    // Configure EC2 LC, ELB, ASG for a build (id: user, fulfillment, splashpage, etc)
    case msg @ BuildActor.Messages.ConfigureAWS => withVerboseErrorHandler(msg) {
      withEnabledBuild { build =>
        configureAWS(build)
      }
    }

    case msg @ BuildActor.Messages.Scale(diffs) => withVerboseErrorHandler(msg) {
      withOrganization { org =>
        withEnabledBuild { build =>
          diffs.foreach { diff =>
            scale(org.docker, build, diff)
          }
        }
      }
    }

    case msg: Any => logUnhandledMessage(msg)

  }

  private[this] def isScaleEnabled(): Boolean = {
    withBuildConfig { buildConfig =>
      buildConfig.stages.contains(BuildStage.Scale)
    }.getOrElse(false)
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
        ecs.scale(awsSettings, imageName, imageVersion, projectName, diff.desiredInstances)
      }

    } else if (diff.lastInstances < diff.desiredInstances) {
      val instances = diff.desiredInstances - diff.lastInstances
      log.runAsync(s"Bring up ${Text.pluralize(instances, "instance", "instances")} of ${diff.versionName}") {
        ecs.scale(awsSettings, imageName, imageVersion, projectName, diff.desiredInstances)
      }
    }
  }

  def captureLastState(build: Build): Future[String] = {
    Logger.info(s"BuildActor[$buildId] captureLastState this.id[$this]")
    log.runAsync("captureLastState") {
      ecs.getClusterInfo(BuildNames.projectName(build)).map { versions =>
        buildLastStatesWriteDao.upsert(
          UsersDao.systemUser,
          build,
          StateForm(versions = versions)
        )
        StateFormatter.label(versions)
      }
    }
  }

  def createLaunchConfiguration(build: Build): Future[String] = {
    log.runSync("EC2 auto scaling group launch configuration") {
      asg.createLaunchConfiguration(awsSettings, BuildNames.projectName(build))
    }
  }

  def createLoadBalancer(build: Build): Future[String] = {
    log.runAsync("EC2 load balancer") {
      elb.createLoadBalancerAndHealthCheck(awsSettings, BuildNames.projectName(build))
    }
  }

  def createAutoScalingGroup(build: Build, launchConfigName: String, loadBalancerName: String): Future[String] = {
    log.runSync("EC2 auto scaling group") {
      asg.createAutoScalingGroup(awsSettings, BuildNames.projectName(build), launchConfigName, loadBalancerName)
    }
  }

  def createCluster(build: Build): Future[String] = {
    log.runSync("Create cluster") {
       ecs.createCluster(BuildNames.projectName(build))
    }
  }

}
