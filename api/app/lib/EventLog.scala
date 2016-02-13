package io.flow.delta.api.lib

import db.{EventsDao, UsersDao}
import io.flow.delta.v0.models.{EventAction, Project}
import io.flow.common.v0.models.User
import java.io.{PrintWriter, StringWriter}
import org.joda.time.DateTime
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

  /**
    * Indicates a start event. Should be followed by a completed event
    * when the function is complete.
    */
  def started(message: String) = {
    println(format(s"started $message"))
    EventsDao.create(user, project.id, EventAction.Started, message, ex = None)
  }

  /**
    * Indicates completion. If there was an error, include the
    * exception. If no exception, we assume successful completion.
    */
  def completed(message: String, error: Option[Throwable] = None) = {
    error match {
      case None => {
        println(format(s"completed $message"))
        EventsDao.create(user, project.id, EventAction.Completed, message, ex = None)
      }
      case Some(ex) => {
        // this works much better
        val sw = new StringWriter
        ex.printStackTrace(new PrintWriter(sw))
        println(format(s"error $message: ${ex.getMessage}\n\n$sw"))

        EventsDao.create(user, project.id, EventAction.Completed, message, ex = Some(ex))
      }
    }
  }

  /**
    * Records a checkpoint - main purpose is to communicate that
    * progress is being made. We intend to build functions that detect
    * failure based on no activity written to the log. So long running
    * functions should periodically checkpoint to track progress in
    * the log.
    */
  def checkpoint(message: String) = {
    println(format(s"checkpoint $message"))
    EventsDao.create(user, project.id, EventAction.Checkpoint, message, ex = None)
  }

  /**
    * Wraps the execution of a function with a started and completed
    * entry in the log. Catches and handles errors as well.
    */
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

  private[this] def format(message: String): String = {
    val ts = new DateTime()
    s"[$ts] ${project.id} $message"
  }
}

