package actors

import aws.EC2ContainerService
import io.flow.play.actors.Util
import play.api.libs.concurrent.Akka
import akka.actor.{Actor,Props}
import play.api.Logger
import play.api.Play.current
import scala.concurrent.ExecutionContext

object ImageActor {

  def props() = Props(new ImageActor())

  lazy val ref = Akka.system.actorOf(props(), "image")

  object Messages {
    case class Deploy(id: String) // Deploy image to AWS
    case class MonitorCreate(id: String) // Monitor newly created ecs service
    case class ScaleUp(id: String) // Scale up to max
    case class MonitorScaleUp(id: String) // Monitor scale up to max
  }

}

class ImageActor extends Actor with Util {

  implicit val imageActorExecutionContext: ExecutionContext = Akka.system.dispatchers.lookup("image-actor-context")

  def receive = {
    case msg @ ImageActor.Messages.Deploy(id: String) => withVerboseErrorHandler(msg.toString) {
      val taskDefinition = registerTaskDefinition(id)
      createService(id, taskDefinition)
      ImageActor.ref ! ImageActor.Messages.MonitorCreate(id)
    }

    case msg @ ImageActor.Messages.MonitorCreate(id: String) => withVerboseErrorHandler(msg.toString) {
      monitorCreatedCanary(id)
    }

    case msg @ ImageActor.Messages.ScaleUp(id: String) => withVerboseErrorHandler(msg.toString) {
      scaleUpService(id)
      ImageActor.ref ! ImageActor.Messages.MonitorScaleUp(id)
    }

    case msg @ ImageActor.Messages.MonitorScaleUp(id: String) => withVerboseErrorHandler(msg.toString) {
      monitorScaleUp(id)
    }
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
      Thread.sleep(5000)
      ImageActor.ref ! ImageActor.Messages.MonitorScaleUp(id)
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
      ImageActor.ref ! ImageActor.Messages.ScaleUp(id)
    } else {
      println(s"[ImageActor.Messages.Monitor] Still Deploying Canary - Image: $id, Service: $status, Running: $running, Pending: $pending, Desired: $desired. Next update in ~5 seconds.")
      println("===========================")
      Thread.sleep(5000)
      ImageActor.ref ! ImageActor.Messages.MonitorCreate(id)
    }
  }

}
