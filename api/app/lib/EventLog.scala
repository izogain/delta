package io.flow.delta.api.lib

import db.UsersDao
import io.flow.delta.v0.models.Project
import io.flow.common.v0.models.User
import scala.util.{Failure, Success, Try}

object EventLog {

  def withSystemUser(
    project: Project,
    prefix: String
  ): EventLog = {
    EventLog(UsersDao.systemUser, project, prefix)
  }

}

case class EventLog(
  user: User,
  project: Project,
  prefix: String
) {

  def started(message: String) = {}
  def completed(message: String, error: Option[Throwable] = None) = {}
  def running(message: String) = {}

  def run(
    message: String
  ) (
    f: => Unit
  ) {
    started(message)

    Try(f) match {
      case Success(result) => {
        completed(message)
      }
      case Failure(ex) => {
        completed(message, Some(ex))
      }
    }
  }

}

