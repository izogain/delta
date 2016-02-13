package io.flow.delta.actors

import io.flow.delta.v0.models.Project
import scala.concurrent.Future

sealed trait SupervisorResult

object SupervisorResult {
  case class NoChange(description: String) extends SupervisorResult
  case class Change(description: String) extends SupervisorResult
}

trait SupervisorFunction {

  def run(
    project: Project
  ) (
    implicit ec: scala.concurrent.ExecutionContext
  ): Future[SupervisorResult]

}
