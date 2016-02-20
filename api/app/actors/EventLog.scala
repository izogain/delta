package io.flow.delta.actors

import io.flow.delta.v0.models.Project

trait EventLog {

  /**
    * The prefix used in entries in the log. By default this is the
    * class name (exluding its package)
    */
  def logPrefix: String = {
    getClass.getName.split("\\.").lastOption match {
      case None => getClass.getName
      case Some(value) => value
    }
  }

  /**
    * Event log relies on a project; this method can be provided by mixing in WithProject
    **/
  def withProject[T](f: Project => T): Option[T]

  lazy val log: io.flow.delta.api.lib.EventLog = {
    withProject {
      io.flow.delta.api.lib.EventLog.withSystemUser(_, logPrefix)
    }.getOrElse {
      sys.error("Cannot access event log without a project")
    }
  }

}
