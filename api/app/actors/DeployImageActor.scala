package io.flow.delta.actors

import aws.EC2ContainerService
import db.{ImagesDao,ProjectsDao}
import io.flow.delta.api.lib.EventLog
import io.flow.delta.v0.models.{Image,Project}
import io.flow.play.actors.Util
import io.flow.postgresql.Authorization
import play.api.libs.concurrent.Akka
import akka.actor.{Actor,Props}
import play.api.Logger
import play.api.Play.current
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

object DeployImageActor {

  trait Message

  object Messages {
    case class Data(id: String) extends Message // Deploy image to AWS
    case object Deploy extends Message // Deploy image to AWS
    case object MonitorCreate extends Message // Monitor newly created ecs service
    case object ScaleUp extends Message // Scale up to max
    case object MonitorScaleUp extends Message // Monitor scale up to max
  }

}

class DeployImageActor extends Actor with Util {

  implicit val imageActorExecutionContext: ExecutionContext = Akka.system.dispatchers.lookup("image-actor-context")

  private[this] var dataProject: Option[Project] = None
  private[this] var dataImage: Option[Image] = None

  private[this] def log: EventLog = {
    dataProject.map { EventLog.withSystemUser(_, "DeployImageActor.Messages.Monitor") }.getOrElse {
      sys.error("Cannot get log with empty data")
    }
  }

  def receive = {
    case msg @ DeployImageActor.Messages.Data(imageId: String) => withVerboseErrorHandler(msg.toString) {
      ImagesDao.findById(Authorization.All, imageId) match {
        case None => {
          dataImage = None
          dataProject = None
        }
        case Some(image) => {
          dataImage = Some(image)
          ProjectsDao.findById(Authorization.All, image.project.id) match {
            case None => {
              dataProject = None
            }
            case Some(project) => {
              dataProject = Some(project)
            }
          }
        }
      }
    }

    case msg @ DeployImageActor.Messages.Deploy => withVerboseErrorHandler(msg.toString) {
        dataImage.foreach { image =>
          val taskDefinition = registerTaskDefinition(image)
          createService(image, taskDefinition)
          self ! DeployImageActor.Messages.MonitorCreate
        }
    }

    case msg @ DeployImageActor.Messages.MonitorCreate => withVerboseErrorHandler(msg.toString) {
        dataImage.foreach { image =>
          monitorCreatedCanary(image)
        }
    }

    case msg @ DeployImageActor.Messages.ScaleUp => withVerboseErrorHandler(msg.toString) {
        dataImage.foreach { image =>
          scaleUpService(image)
          monitorScaleUp(image)
        }
    }

    case msg @ DeployImageActor.Messages.MonitorScaleUp => withVerboseErrorHandler(msg.toString) {
        dataImage.foreach { image =>
          monitorScaleUp(image)
        }
    }

    case msg: Any => logUnhandledMessage(msg)
  }

  def scaleUpService(image: Image) {
    EC2ContainerService.scaleUp(image.id, image.project.name)
  }

  def registerTaskDefinition(image: Image): String = {
    log.started(s"Registering task definition: [${image.id}]")
    val taskDefinition = EC2ContainerService.registerTaskDefinition(image.id, image.project.name)
    log.completed(s"Task Registered: [${image.id}], Task: [${taskDefinition}]")
    taskDefinition
  }

  def createService(image: Image, taskDefinition: String) {
    log.started(s"Creating service: [${image.id}]")
    val service = EC2ContainerService.createService(image.id, image.project.name, taskDefinition)
    log.completed(s"Service Created: [${image.id}], Service: [${service}]")
  }

  def monitorScaleUp(image: Image) {
    val ecsService = EC2ContainerService.getServiceInfo(image.id, image.project.name)
    val running = ecsService.getRunningCount
    val desired = ecsService.getDesiredCount
    val pending = ecsService.getPendingCount

    val status = ecsService.getStatus
    if (running == desired) {
      log.completed(s"Deploying Scaling Up - Image: ${image.id}, Service: $status, Running: $running, Pending: $pending, Desired: $desired.")
    } else {
      log.running(s"Still Scaling Up - Image: ${image.id}, Service: $status, Running: $running, Pending: $pending, Desired: $desired. Next update in ~5 seconds.")

      Akka.system.scheduler.scheduleOnce(Duration(5, "seconds")) {
        self ! DeployImageActor.Messages.MonitorScaleUp
      }
    }
  }

  def monitorCreatedCanary(image: Image) {
    val ecsService = EC2ContainerService.getServiceInfo(image.id, image.project.name)
    val running = ecsService.getRunningCount
    val desired = ecsService.getDesiredCount
    val pending = ecsService.getPendingCount

    val status = ecsService.getStatus
    if (running == desired) {
      log.completed(s"Completed Deploying Canary - Image: ${image.id}, Service: $status, Running: $running, Pending: $pending, Desired: $desired. Scaling up to max now.")
      self ! DeployImageActor.Messages.ScaleUp
    } else {
      log.running(s"Still Deploying Canary - Image: ${image.id}, Service: $status, Running: $running, Pending: $pending, Desired: $desired. Next update in ~5 seconds.")

      Akka.system.scheduler.scheduleOnce(Duration(5, "seconds")) {
        self ! DeployImageActor.Messages.MonitorCreate
      }
    }
  }

}
