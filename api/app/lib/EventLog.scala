package io.flow.delta.api.lib

import db.UsersDao
import io.flow.delta.v0.models.Project
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

  def started(message: String) = {
    println(format(s"started $message"))
  }

  def completed(message: String, error: Option[Throwable] = None) = {
    error match {
      case None => {
        println(format(s"completed $message"))
      }
      case Some(ex) => {
        // this works much better
        val sw = new StringWriter
        ex.printStackTrace(new PrintWriter(sw))
        println(format(s"error $message: ${ex.getMessage}\n\n$sw"))
      }
    }
  }

  def running(message: String) = {
    println(format(s"running $message"))
  }

  private[this] def format(message: String): String = {
    val ts = new DateTime()
    s"[$ts] ${project.id} $message"
  }

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

