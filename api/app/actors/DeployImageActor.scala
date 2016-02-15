package io.flow.delta.actors

import io.flow.delta.aws.EC2ContainerService
import db.{ImagesDao,ProjectsDao}
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

class DeployImageActor extends Actor with Util with DataProject with EventLog {

  implicit val imageActorExecutionContext: ExecutionContext = Akka.system.dispatchers.lookup("image-actor-context")

  private[this] var dataImage: Option[Image] = None

  override val logPrefix = "DeployImageActor"

  def receive = {
    case msg @ DeployImageActor.Messages.Data(imageId: String) => withVerboseErrorHandler(msg.toString) {
      ImagesDao.findById(imageId) match {
        case None => {
          dataImage = None
        }
        case Some(image) => {
          dataImage = Some(image)
          setDataProject(image.project.id)
        }
      }
    }

    case msg @ DeployImageActor.Messages.Deploy => withVerboseErrorHandler(msg.toString) {
        dataImage.foreach { image =>
          val taskDefinition = registerTaskDefinition(image)
          createService(image, taskDefinition)
          self ! DeployImageActor.Messages.ScaleUp
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
    EC2ContainerService.scaleUp(image.id, image.version, image.project.id)
  }

  // For registerTaskDefinition, createService, monitorScaleUp:
  // image.id (example: "flowcommerce/user")
  // image.version (example: "0.0.12")
  // image.project.id (example: "user")
  def registerTaskDefinition(image: Image): String = {
    log.started(s"Registering task definition: [${image.id}]")
    val taskDefinition = EC2ContainerService.registerTaskDefinition(image.id, image.version, image.project.id)
    log.completed(s"Task Registered: [${image.id}], Task: [${taskDefinition}]")
    taskDefinition
  }

  def createService(image: Image, taskDefinition: String) {
    log.started(s"Creating service: [${image.id}]")
    val service = EC2ContainerService.createService(image.id, image.version, image.project.id, taskDefinition)
    log.completed(s"Service Created: [${image.id}], Service: [${service}]")
  }

  def monitorScaleUp(image: Image) {
    val ecsService = EC2ContainerService.getServiceInfo(image.id, image.version, image.project.id)
    val running = ecsService.getRunningCount
    val desired = ecsService.getDesiredCount
    val pending = ecsService.getPendingCount

    val status = ecsService.getStatus
    if (running == desired) {
      log.completed(s"Deploying Scaling Up - Image: ${image.id}, Service: $status, Running: $running, Pending: $pending, Desired: $desired.")
    } else {
      log.checkpoint(s"Still Scaling Up - Image: ${image.id}, Service: $status, Running: $running, Pending: $pending, Desired: $desired. Next update in ~5 seconds.")

      Akka.system.scheduler.scheduleOnce(Duration(5, "seconds")) {
        self ! DeployImageActor.Messages.MonitorScaleUp
      }
    }
  }

}
