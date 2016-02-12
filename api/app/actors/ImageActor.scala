package io.flow.delta.actors

import aws.EC2ContainerService
import io.flow.play.actors.Util
import play.api.libs.concurrent.Akka
import akka.actor.{Actor,Props}
import play.api.Logger
import play.api.Play.current
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

object ImageActor {

  trait Message

  object Messages {
    case class Data(id: String) extends Message // Deploy image to AWS
    case object Deploy extends Message // Deploy image to AWS
    case object MonitorCreate extends Message // Monitor newly created ecs service
    case object ScaleUp extends Message // Scale up to max
    case object MonitorScaleUp extends Message // Monitor scale up to max
  }

}

class ImageActor extends Actor with Util {

  implicit val imageActorExecutionContext: ExecutionContext = Akka.system.dispatchers.lookup("image-actor-context")

  private[this] var dataImage: Option[String] = None

  def receive = {
    case msg @ ImageActor.Messages.Data(id: String) => withVerboseErrorHandler(msg.toString) {
      dataImage = Some(id)
    }

    case msg @ ImageActor.Messages.Deploy => withVerboseErrorHandler(msg.toString) {
      dataImage.foreach { id =>
        val taskDefinition = registerTaskDefinition(id)
        createService(id, taskDefinition)
        self ! ImageActor.Messages.MonitorCreate
      }
    }

    case msg @ ImageActor.Messages.MonitorCreate => withVerboseErrorHandler(msg.toString) {
      dataImage.foreach { id =>
        monitorCreatedCanary(id)
      }
    }

    case msg @ ImageActor.Messages.ScaleUp => withVerboseErrorHandler(msg.toString) {
      dataImage.foreach { id =>
        scaleUpService(id)
        monitorScaleUp(id)
      }
    }

    case msg @ ImageActor.Messages.MonitorScaleUp => withVerboseErrorHandler(msg.toString) {
      dataImage.foreach { id =>
        monitorScaleUp(id)
      }
    }

    case msg: Any => logUnhandledMessage(msg)
  }

  def scaleUpService(id: String) {
    EC2ContainerService.scaleUp(id)
  }

  def registerTaskDefinition(id: String): String = {
    val taskDefinition = EC2ContainerService.registerTaskDefinition(id)
    println(s"[ImageActor.Messages.Deploy] Done - Task Registered: [$id], Task: [${taskDefinition}]")
    return taskDefinition
  }

  def createService(id: String, taskDefinition: String) {
    val service = EC2ContainerService.createService(id, taskDefinition)
    println(s"[ImageActor.Messages.Deploy] Done - Service Created: [$id], Service: [${service}]")
  }

  def monitorScaleUp(id: String) {
    val ecsService = EC2ContainerService.getServiceInfo(id)
    val running = ecsService.getRunningCount
    val desired = ecsService.getDesiredCount
    val pending = ecsService.getPendingCount

    val status = ecsService.getStatus
    if (running == desired) {
      println(s"[ImageActor.Messages.Monitor] DONE Deploying Scaling Up - Image: $id, Service: $status, Running: $running, Pending: $pending, Desired: $desired.")
      println("===========================")
    } else {
      println(s"[ImageActor.Messages.Monitor] Still Scaling Up - Image: $id, Service: $status, Running: $running, Pending: $pending, Desired: $desired. Next update in ~5 seconds.")
      println("===========================")

      Akka.system.scheduler.scheduleOnce(Duration(5, "seconds")) {
        self ! ImageActor.Messages.MonitorScaleUp
      }
    }
  }

  def monitorCreatedCanary(id: String) {
    val ecsService = EC2ContainerService.getServiceInfo(id)
    val running = ecsService.getRunningCount
    val desired = ecsService.getDesiredCount
    val pending = ecsService.getPendingCount

    val status = ecsService.getStatus
    if (running == desired) {
      println(s"[ImageActor.Messages.Monitor] DONE Deploying Canary - Image: $id, Service: $status, Running: $running, Pending: $pending, Desired: $desired. Scaling up to max now.")
      println("===========================")
      self ! ImageActor.Messages.ScaleUp
    } else {
      println(s"[ImageActor.Messages.Monitor] Still Deploying Canary - Image: $id, Service: $status, Running: $running, Pending: $pending, Desired: $desired. Next update in ~5 seconds.")
      println("===========================")

      Akka.system.scheduler.scheduleOnce(Duration(5, "seconds")) {
        self ! ImageActor.Messages.MonitorCreate
      }
    }
  }

}
