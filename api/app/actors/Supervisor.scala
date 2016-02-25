package io.flow.delta.actors

import io.flow.delta.v0.models.{Build, Project, Settings}
import scala.concurrent.Future

sealed trait SupervisorResult

object SupervisorResult {
  case class Ready(description: String) extends SupervisorResult
  case class Change(description: String) extends SupervisorResult
  case class Checkpoint(description: String) extends SupervisorResult  
  case class Error(description: String, ex: Option[Throwable] = None) extends SupervisorResult
}

trait ProjectSupervisorFunction {

  /**
   * Responsible for actually running this function
   */
  def run(
    project: Project
  ) (
    implicit ec: scala.concurrent.ExecutionContext
  ): Future[SupervisorResult]

}

trait BuildSupervisorFunction {

  /**
   * Responsible for actually running this function
   */
  def run(
    build: Build
  ) (
    implicit ec: scala.concurrent.ExecutionContext
  ): Future[SupervisorResult]

}

