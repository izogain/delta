package io.flow.delta.actors

import db.BuildsDao
import io.flow.delta.v0.models.Build
import io.flow.postgresql.Authorization
import play.api.Logger

trait DataBuild extends DataProject {

  private[this] var dataBuild: Option[Build] = None

  /**
    * Looks up the build with the specified ID, setting the local
    * dataBuild var to that build
    */
  def setBuildId(id: String) {
    BuildsDao.findById(Authorization.All, id) match {
      case None => {
        dataBuild = None
        Logger.warn(s"Could not find build with id[$id]")
      }
      case Some(b) => {
        setProjectId(b.project.id)
        dataBuild = Some(b)
      }
    }
  }

  /**
    * Invokes the specified function w/ the current build, but only
    * if we have a build set.
    */
  def withBuild[T](f: Build => T): Option[T] = {
    dataBuild.map { f(_) }
  }

}
