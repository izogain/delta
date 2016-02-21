package io.flow.delta.actors.functions

import db.{ImagesDao, ProjectDesiredStatesDao}
import io.flow.delta.actors.{MainActor, MainActorProvider, SupervisorFunction, SupervisorResult}
import io.flow.postgresql.Authorization
import io.flow.delta.v0.models.Project
import play.api.Logger
import play.libs.Akka
import akka.actor.{Actor, ActorRef}
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

/**
  * Looks up the desired state for a project. If found, checks to see
  * if we already have a docker image locally for each version in the
  * desired stated, triggering docker builds for each image that is
  * not found locally.
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

}

case class BuildDockerImage(project: Project) {

  def run(
    implicit ec: scala.concurrent.ExecutionContext
  ): SupervisorResult = {
    ProjectDesiredStatesDao.findByProjectId(Authorization.All, project.id) match {
      case None => {
        SupervisorResult.NoChange("Project does not have a desired state")
      }

      case Some(state) => {
        val versions = state.versions.flatMap { version =>
          ImagesDao.findByProjectIdAndVersion(project.id, version.name) match {
            case Some(i) => {
              None
            }
            case None => {
              MainActorProvider.ref ! MainActor.Messages.BuildDockerImage(project.id, version.name)
              Some(version.name)
            }
          }
        }

        versions.toList match {
          case Nil => {
            SupervisorResult.NoChange(s"All images exist for versions in desired state[%s]".format(state.versions.map(_.name).mkString(", ")))
          }
          case one :: Nil => {
            SupervisorResult.Change(s"Started build of docker image for version $one")
          }
          case multiple => {
            SupervisorResult.Change(s"Started build of docker images for versions %s".format(multiple.mkString(", ")))
          }
        }
      }
    }
  }

}
