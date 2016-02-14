package io.flow.delta.actors.functions

import db.{ShasDao, SettingsDao, TagsDao, UsersDao}
import io.flow.delta.actors.{SupervisorFunction, SupervisorResult}
import io.flow.delta.api.lib.{Email, Semver}
import io.flow.github.v0.models.{RefForm, TagForm, Tagger, TagSummary}
import io.flow.postgresql.Authorization
import io.flow.delta.api.lib.GithubUtil
import io.flow.delta.v0.models.{Project, Settings}
import org.joda.time.DateTime
import play.api.Logger
import play.libs.Akka
import akka.actor.Actor
import scala.concurrent.Future

/**
  * Looks up the latest tag for a project and ensures that there is a
  * docker image being built for it.
  */
object BuildDockerImage extends SupervisorFunction {

  override def run(
    project: Project
  ) (
    implicit ec: scala.concurrent.ExecutionContext
  ): Future[SupervisorResult] = {
    BuildDockerImage(project).run
  }

  override def isEnabled(settings: Settings) = true // TODO: settings.buildDockerImage

}

case class BuildDockerImage(project: Project) extends Github {

  private[this] val repo = GithubUtil.parseUri(project.uri).right.getOrElse {
    sys.error(s"Project id[${project.id}] uri[${project.uri}]: Could not parse")
  }

  def run(
    implicit ec: scala.concurrent.ExecutionContext
  ): Future[SupervisorResult] = {
    TagsDao.findLatestByProjectId(Authorization.All, project.id) match {
      case None => {
        Future {
          SupervisorResult.NoChange("Project does not have any tags")
        }
      }

      case Some(tag) => {
        Future { SupervisorResult.NoChange(s"TODO: ${tag.name}") }
      }
    }
  }

}
