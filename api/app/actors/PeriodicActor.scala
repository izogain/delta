package io.flow.delta.actors

import db.ProjectsDao
import io.flow.play.actors.Util
import io.flow.postgresql.{Authorization, Pager}
import play.api.Logger
import akka.actor.Actor

object PeriodicActor {

  sealed trait Message

  object Messages {
    case object CheckProjects extends Message
  }

}

class PeriodicActor extends Actor with Util {

  def receive = {

    case m @ PeriodicActor.Messages.CheckProjects => withVerboseErrorHandler(m) {
      Pager.create { offset =>
        ProjectsDao.findAll(Authorization.All, offset = offset)
      }.foreach { project =>
        sender ! MainActor.Messages.ProjectSync(project.id)
      }
    }

    case m: Any => logUnhandledMessage(m)
  }

}
