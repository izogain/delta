package io.flow.delta.actors

import com.amazonaws.services.ecs.model.Service
import io.flow.postgresql.Authorization
import org.joda.time.DateTime
import io.flow.delta.api.lib.{Semver, StateFormatter}
import io.flow.delta.aws.{AutoScalingGroup, EC2ContainerService, ElasticLoadBalancer}
import db.{OrganizationsDao, TokensDao, UsersDao, BuildLastStatesWriteDao}
import io.flow.delta.api.lib.{GithubHelper, RegistryClient, Repo, StateDiff}
import io.flow.delta.v0.models.{Build, StateForm}
import io.flow.delta.lib.Text
import io.flow.play.actors.ErrorHandler
import io.flow.play.util.DefaultConfig
import play.api.Logger
import play.libs.Akka
import akka.actor.Actor
import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success, Try}
import scala.concurrent.duration._
import scala.concurrent.Future

object BuildActor {

  val CheckLastStateIntervalSeconds = 45

  trait Message

  object Messages {
    case object CheckLastState extends Message

    case object ConfigureAWS extends Message // One-time AWS setup

    case class MonitorScale(imageName: String, imageVersion: String) extends Message

    case class Scale(diffs: Seq[StateDiff]) extends Message

    case object Setup extends Message    
  }

  trait Factory {
    def apply(buildId: String): Actor
  }

}

class BuildActor @javax.inject.Inject() (
  registryClient: RegistryClient,
  config: DefaultConfig,
  @com.google.inject.assistedinject.Assisted buildId: String
) extends Actor with ErrorHandler with DataBuild with EventLog {

  implicit val buildActorExecutionContext: ExecutionContext = Akka.system.dispatchers.lookup("build-actor-context")

  private[this] lazy val ecs = EC2ContainerService(registryClient)
  private[this] lazy val elb = ElasticLoadBalancer(registryClient)
  private[this] lazy val asg = AutoScalingGroup(
    ecs,
    dockerHubToken = config.requiredString("dockerhub.delta.auth.token"),
    dockerHubEmail = config.requiredString("dockerhub.delta.auth.email")
  )

  def receive = {

    // case msg @ BuildActor.Messages.Data(id) => withVerboseErrorHandler(msg) {
    case msg @ BuildActor.Messages.Setup => withVerboseErrorHandler(msg) {
      setBuildId(buildId)

      if (isScaleEnabled) {
        self ! BuildActor.Messages.ConfigureAWS

        withBuild { build =>
          Akka.system.scheduler.schedule(
            Duration(1, "second"),
            Duration(BuildActor.CheckLastStateIntervalSeconds, "seconds")
          ) {
            self ! BuildActor.Messages.CheckLastState
          }
        }
      }
    }

    case msg @ BuildActor.Messages.CheckLastState => withVerboseErrorHandler(msg) {
      withBuild { build =>
        captureLastState(build)
      }
    }

    // Configure EC2 LC, ELB, ASG for a build (id: user, fulfillment, splashpage, etc)
    case msg @ BuildActor.Messages.ConfigureAWS => withVerboseErrorHandler(msg) {
      withBuild { build =>
        configureAWS(build)
      }
    }

    case msg @ BuildActor.Messages.Scale(diffs) => withVerboseErrorHandler(msg) {
      withBuild { build =>
        diffs.foreach { diff =>
          scale(build, diff)
        }
      }
    }

    case msg @ BuildActor.Messages.MonitorScale(imageName, imageVersion) => withVerboseErrorHandler(msg) {
      withBuild { build =>
        monitorScale(build, imageName, imageVersion)
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

  def scale(build: Build, diff: StateDiff) {
    val org = OrganizationsDao.findById(Authorization.All, build.project.organization.id).getOrElse {
      sys.error("Build organization[${build.project.organization.id}] does not exist")
    }
    val imageName = s"${org.docker.organization}/${build.name}"
    val imageVersion = diff.versionName

    if (diff.lastInstances > diff.desiredInstances) {
      val instances = diff.lastInstances - diff.desiredInstances
      log.runSync(s"Bring down ${Text.pluralize(instances, "instance", "instances")} of ${diff.versionName}") {
        ecs.scale(imageName, imageVersion, build.name, diff.desiredInstances)
      }

    } else if (diff.lastInstances < diff.desiredInstances) {
      val instances = diff.desiredInstances - diff.lastInstances
      log.runSync(s"Bring up ${Text.pluralize(instances, "instance", "instances")} of ${diff.versionName}") {
        ecs.scale(imageName, imageVersion, build.name, diff.desiredInstances)
      }
    }

    monitorScale(build, imageName, imageVersion)
  }

  def monitorScale(build: Build, imageName: String, imageVersion: String) {
    captureLastState(build)

    for {
      ecsServiceOpt <- getServiceInfo(imageName, imageVersion, build)
    } yield {
      ecsServiceOpt match {
        case None => {
          sys.error(s"ECS Service not found for build $build.id, image $imageName, version $imageVersion")
        }

        case Some(service) => {
          val summary = ecs.summary(service)
          val intervalSeconds = 5

          if (service.getRunningCount == service.getDesiredCount) {
            log.completed(s"Scaling ${imageName}, Version: ${imageVersion}, $summary")

          } else {
            log.checkpoint(s"Scaling ${imageName}, Version: ${imageVersion}, $summary")

            Akka.system.scheduler.scheduleOnce(Duration(intervalSeconds, "seconds")) {
              self ! BuildActor.Messages.MonitorScale(imageName, imageVersion)
            }
          }
        }
      }
    }
  }

  def ecsName(build: Build): String = {
    "%s-%s".format(build.project.id, build.name)
  }

  def captureLastState(build: Build): Future[String] = {
    log.runAsync("captureLastState") {
      ecs.getClusterInfo(ecsName(build)).map { versions =>
        play.api.Play.current.injector.instanceOf[BuildLastStatesWriteDao].upsert(
          UsersDao.systemUser,
          build,
          StateForm(versions = versions)
        )
        StateFormatter.label(versions)
      }
    }
  }

  def getServiceInfo(imageName: String, imageVersion: String, build: Build): Future[Option[Service]] = {
    log.runSync("Getting ECS service Info", quiet = true) {
      ecs.getServiceInfo(imageName, imageVersion, ecsName(build))
    }
  }

  def createLaunchConfiguration(build: Build): Future[String] = {
    log.runSync("EC2 auto scaling group launch configuration") {
      asg.createLaunchConfiguration(ecsName(build))
    }
  }

  def createLoadBalancer(build: Build): Future[String] = {
    log.runAsync("EC2 load balancer") {
      elb.createLoadBalancerAndHealthCheck(ecsName(build))
    }
  }

  def createAutoScalingGroup(build: Build, launchConfigName: String, loadBalancerName: String): Future[String] = {
    log.runSync("EC2 auto scaling group") {
      asg.createAutoScalingGroup(ecsName(build), launchConfigName, loadBalancerName)
    }
  }

  def createCluster(build: Build): Future[String] = {
    log.runSync("Create cluster") {
       ecs.createCluster(ecsName(build))
    }
  }

}
