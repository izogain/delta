package io.flow.delta.actors.functions

import db.{ImagesDao, TagsDao, UsersDao}
import io.flow.delta.actors.{MainActor, EventLog, SupervisorFunction, SupervisorResult}
import io.flow.postgresql.Authorization
import io.flow.delta.api.lib.GithubUtil
import io.flow.delta.v0.models.{Project, Settings}
import play.api.Logger
import play.libs.Akka
import akka.actor.Actor
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

/**
  * Looks up the latest tag for a project. If found, checks to see if
  * we already have a docker image locally for that tag. If not,
  * ensures that there is a docker image being built for it.
  */
object BuildDockerImage extends SupervisorFunction {

  override def run(
    project: Project
  ) (
    implicit ec: scala.concurrent.ExecutionContext
  ): Future[SupervisorResult] = {
    Future {
      BuildDockerImage(project).run
    }
  }

  override def isEnabled(settings: Settings) = true // TODO: settings.buildDockerImage

}

case class BuildDockerImage(project: Project) extends Github with EventLog {

  def logPrefix = "BuildDockerImage"

  def withProject[T](f: Project => T): Option[T] = {
    Some(f(project))
  }

  private[this] val repo = GithubUtil.parseUri(project.uri).right.getOrElse {
    sys.error(s"Project id[${project.id}] uri[${project.uri}]: Could not parse")
  }

  def run(
    implicit ec: scala.concurrent.ExecutionContext
  ): SupervisorResult = {
    TagsDao.findLatestByProjectId(Authorization.All, project.id) match {
      case None => {
        SupervisorResult.NoChange("Project does not have any tags")
      }

      case Some(tag) => {
        ImagesDao.findByProjectIdAndVersion(project.id, tag.name) match {
          case Some(i) => {
            SupervisorResult.NoChange(s"Image ${repo}:${tag.name} already exist")
          }
          case None => {
            MainActor.ref ! MainActor.Messages.BuildDockerImage(project.id, tag.name)
            SupervisorResult.Change(s"Started build of docker image ${repo}:${tag.name}")
          }
        }
      }
    }
  }

}
