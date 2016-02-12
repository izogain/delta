package io.flow.delta.actors

import io.flow.delta.aws._
import io.flow.postgresql.Authorization
import db.ProjectsDao
import io.flow.delta.api.lib.EventLog
import io.flow.delta.v0.models.Project
import io.flow.play.actors.Util
import play.api.Logger
import play.libs.Akka
import akka.actor.Actor
import scala.concurrent.ExecutionContext

object SupervisorActor {

  trait Message

  object Messages {
    case class Data(id: String) extends Message
    case object PursueExpectedState extends Message
  }

}

class SupervisorActor extends Actor with Util {

  implicit val supervisorActorExecutionContext: ExecutionContext = Akka.system.dispatchers.lookup("supervisor-actor-context")

  private[this] var dataProject: Option[Project] = None

  private[this] def log: EventLog = {
    dataProject.map { EventLog.withSystemUser(_, "SupervisorActor") }.getOrElse {
      sys.error("Cannot get log with empty data")
    }
  }

  def receive = {

    case m @ SupervisorActor.Messages.Data(id) => withVerboseErrorHandler(m.toString) {
      dataProject = ProjectsDao.findById(Authorization.All, id)
    }

    case msg @ SupervisorActor.Messages.PursueExpectedState => {
      dataProject.foreach { project =>
        println(s"Pursuing expected state for project: $project")
      }
    }

  }

}
