package io.flow.delta.actors

import aws.EC2ContainerService
import db.ProjectsDao
import io.flow.delta.api.lib.EventLog
import io.flow.delta.v0.models.Project
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
    case class Data(projectId: String, id: String) extends Message // Deploy image to AWS
    case object Deploy extends Message // Deploy image to AWS
    case object MonitorCreate extends Message // Monitor newly created ecs service
    case object ScaleUp extends Message // Scale up to max
    case object MonitorScaleUp extends Message // Monitor scale up to max
  }

}

class DeployImageActor extends Actor with Util {

  implicit val imageActorExecutionContext: ExecutionContext = Akka.system.dispatchers.lookup("image-actor-context")

  private[this] var dataImage: Option[String] = None
  private[this] var dataProject: Option[Project] = None

  private[this] def log: EventLog = {
    dataProject.map { EventLog.withSystemUser(_, "DeployImageActor.Messages.Monitor") }.getOrElse {
      sys.error("Cannot get log with empty data")
    }
  }

  def receive = {
    case msg @ DeployImageActor.Messages.Data(projectId: String, imageId: String) => withVerboseErrorHandler(msg.toString) {
      ProjectsDao.findById(Authorization.All, projectId) match {
        case None => {
          dataImage = None
          dataProject = None
        }
        case Some(project) => {
          dataImage = Some(imageId)
          dataProject = Some(project)
        }
      }
    }

    case msg @ DeployImageActor.Messages.Deploy => withVerboseErrorHandler(msg.toString) {
      dataImage.foreach { id =>
        val taskDefinition = registerTaskDefinition(id)
        createService(id, taskDefinition)
        self ! DeployImageActor.Messages.MonitorCreate
      }
    }

    case msg @ DeployImageActor.Messages.MonitorCreate => withVerboseErrorHandler(msg.toString) {
      dataImage.foreach { id =>
        monitorCreatedCanary(id)
      }
    }

    case msg @ DeployImageActor.Messages.ScaleUp => withVerboseErrorHandler(msg.toString) {
      dataImage.foreach { id =>
        scaleUpService(id)
        monitorScaleUp(id)
      }
    }

    case msg @ DeployImageActor.Messages.MonitorScaleUp => withVerboseErrorHandler(msg.toString) {
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
    log.started(s"Registering task definition: [$id]")
    val taskDefinition = EC2ContainerService.registerTaskDefinition(id)
    log.completed(s"Task Registered: [$id], Task: [${taskDefinition}]")
    taskDefinition
  }

  def createService(id: String, taskDefinition: String) {
    log.started(s"Creating service: [$id]")
    val service = EC2ContainerService.createService(id, taskDefinition)
    log.completed(s"Service Created: [$id], Service: [${service}]")
  }

  def monitorScaleUp(id: String) {
    val ecsService = EC2ContainerService.getServiceInfo(id)
    val running = ecsService.getRunningCount
    val desired = ecsService.getDesiredCount
    val pending = ecsService.getPendingCount

    val status = ecsService.getStatus
    if (running == desired) {
      log.completed("Deploying Scaling Up - Image: $id, Service: $status, Running: $running, Pending: $pending, Desired: $desired.")
    } else {
      log.running("Still Scaling Up - Image: $id, Service: $status, Running: $running, Pending: $pending, Desired: $desired. Next update in ~5 seconds.")

      Akka.system.scheduler.scheduleOnce(Duration(5, "seconds")) {
        self ! DeployImageActor.Messages.MonitorScaleUp
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
      log.completed("Completed Deploying Canary - Image: $id, Service: $status, Running: $running, Pending: $pending, Desired: $desired. Scaling up to max now.")
      self ! DeployImageActor.Messages.ScaleUp
    } else {
      log.running("Still Deploying Canary - Image: $id, Service: $status, Running: $running, Pending: $pending, Desired: $desired. Next update in ~5 seconds.")

      Akka.system.scheduler.scheduleOnce(Duration(5, "seconds")) {
        self ! DeployImageActor.Messages.MonitorCreate
      }
    }
  }

}
