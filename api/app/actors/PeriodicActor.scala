package io.flow.delta.actors

import db.{EventsDao, ProjectsDao}
import io.flow.play.actors.Util
import io.flow.postgresql.{Authorization, OrderBy, Pager}
import play.api.Logger
import akka.actor.Actor

object PeriodicActor {

  sealed trait Message

  object Messages {
    case object CheckProjects extends Message
  }

}

class PeriodicActor extends Actor with Util {

  private[this] val MinutesUntilInactive = 2
  
  def receive = {

    case m @ PeriodicActor.Messages.CheckProjects => withVerboseErrorHandler(m) {
      Pager.create { offset =>
        ProjectsDao.findAll(Authorization.All, offset = offset)
      }.foreach { project =>
        isActive(project.id) match {
          case true => {
            Logger.info(s"PeriodicActor: Project[${project.id}] is already active")
          }
          case false => {
            MainActor.ref ! MainActor.Messages.ProjectSync(project.id)
          }
        }
      }
    }

    case m: Any => logUnhandledMessage(m)
  }


  /**
   * A project is considered active if:
   * 
   *   - it has had at least one log entry written in the past MinutesUntilInactive minutes
   *   - the last log entry writtin was not the successful completion of the supervisor
   *     actor loop (SupervisorActor.SuccessfulCompletionMessage)
   * 
   * Otherwise, the project is not active
   */
  private[this] def isActive(projectId: String): Boolean = {
    EventsDao.findAll(
      projectId = Some(projectId),
      numberMinutesSinceCreation = Some(MinutesUntilInactive),
      limit = 1,
      orderBy = OrderBy("-events.created_at")
    ).headOption match {
      case None => {
        false
      }
      case Some(event) => {
        event.summary != SupervisorActor.SuccessfulCompletionMessage
      }
    }
  }
  
}
