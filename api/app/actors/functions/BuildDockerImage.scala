package io.flow.delta.actors.functions

import db.{ImagesDao, BuildDesiredStatesDao}
import io.flow.delta.actors.{MainActor, MainActorProvider, SupervisorBuildFunction, SupervisorResult}
import io.flow.postgresql.Authorization
import io.flow.delta.v0.models.Build
import play.api.Logger
import play.libs.Akka
import akka.actor.{Actor, ActorRef}
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

/**
  * Looks up the desired state for a build. If found, checks to see
  * if we already have a docker image locally for each version in the
  * desired stated, triggering docker builds for each image that is
  * not found locally.
  */
object BuildDockerImage extends SupervisorBuildFunction {

  override def run(
    build: Build
  ) (
    implicit ec: scala.concurrent.ExecutionContext
  ): Future[SupervisorResult] = {
    Future {
      BuildDockerImage(build).run
    }
  }

}

case class BuildDockerImage(build: Build) {

  def run(
    implicit ec: scala.concurrent.ExecutionContext
  ): SupervisorResult = {
    BuildDesiredStatesDao.findByBuildId(Authorization.All, build.id) match {
      case None => {
        SupervisorResult.NoChange("Build does not have a desired state")
      }

      case Some(state) => {
        val versions = state.versions.flatMap { version =>
          ImagesDao.findByBuildIdAndVersion(build.id, version.name) match {
            case Some(i) => {
              None
            }
            case None => {
              MainActorProvider.ref ! MainActor.Messages.BuildDockerImage(build.id, version.name)
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
