package actors

import aws.EC2ContainerService
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
    case class Monitor(id: String) // Monitor the ongoing deploy
  }

}

class ImageActor extends Actor with Util {

  implicit val imageActorExecutionContext: ExecutionContext = Akka.system.dispatchers.lookup("image-actor-context")

  def receive = {
    // id: Docker image style ID
    // examples: flowcommerce/user:0.0.3, giltarchitecture/ubuntu: 1.2.34
    case msg @ ImageActor.Messages.Deploy(id: String) => withVerboseErrorHandler(msg.toString) {
      val taskDefinition = registerTaskDefinition(id)
      println(s"[ImageActor.Messages.Deploy] Done - Task Registered: [$id], Task: [${taskDefinition}]")

      val service = createService(id, taskDefinition)
      println(s"[ImageActor.Messages.Deploy] Done - Service Created: [$id], Service: [${service}]")

      ImageActor.ref ! ImageActor.Messages.Monitor(id)
    }

    case msg @ ImageActor.Messages.Monitor(id: String) => withVerboseErrorHandler(msg.toString) {
      val ecsService = EC2ContainerService.getServiceInfo(id)
      val runningCount = ecsService.getRunningCount
      val desiredCount = ecsService.getDesiredCount
      val status = ecsService.getStatus
      if (runningCount == desiredCount) {
        println(s"[ImageActor.Messages.Monitor] DONE Deploying - Image: $id, Service: $status, Running: $runningCount, Desired: $desiredCount.")
        println("===========================")
      } else {
        println(s"[ImageActor.Messages.Monitor] Still Deploying - Image: $id, Service: $status, Running: $runningCount, Desired: $desiredCount. Next update in 5 seconds.")
        println("===========================")
        Thread.sleep(5000)
        ImageActor.ref ! ImageActor.Messages.Monitor(id)
      }
    }
  }

  def registerTaskDefinition(id: String): String = {
    return EC2ContainerService.registerTaskDefinition(id)
  }

  def createService(id: String, taskDefinition: String): String = {
    return EC2ContainerService.createService(id, taskDefinition)
  }

}
