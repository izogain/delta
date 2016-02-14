package io.flow.delta.actors

import io.flow.delta.v0.models.{Project, Settings}
import scala.concurrent.Future

sealed trait SupervisorResult

object SupervisorResult {
  case class NoChange(description: String) extends SupervisorResult
  case class Change(description: String) extends SupervisorResult
  case class Error(description: String, ex: Throwable) extends SupervisorResult
}

trait SupervisorFunction {

  /**
   * Responsible for actually running this function
   */
  def run(
    project: Project
  ) (
    implicit ec: scala.concurrent.ExecutionContext
  ): Future[SupervisorResult]

  /**
   * Returns true if enabled based on project settings, false
   * otherwise.
   */
  def isEnabled(settings: Settings): Boolean

}
