package io.flow.delta.actors.functions

import db.{ProjectLastStatesDao, ProjectDesiredStatesDao}
import io.flow.delta.actors.{MainActor, SupervisorFunction, SupervisorResult}
import io.flow.postgresql.Authorization
import io.flow.delta.v0.models.Project
import org.joda.time.DateTime
import scala.concurrent.Future

/**
  * If we have both an desired state and a recent actual state,
  * compares the two to see if there are any instances we need to
  * scale up or down in production. Scale Up will always happen first;
  * scale down only initiatied after Scale Up is complete.
  */
object Scale extends SupervisorFunction {

  private[this] val MinutesUntilStale = 10

  override def run(
    project: Project
  ) (
    implicit ec: scala.concurrent.ExecutionContext
  ): Future[SupervisorResult] = {
    val lastState = ProjectLastStatesDao.findByProjectId(Authorization.All, project.id)
    val desiredState = ProjectDesiredStatesDao.findByProjectId(Authorization.All, project.id)
    Future {
      (lastState, desiredState) match {

        case (_, None) => {
          SupervisorResult.NoChange(s"Desired state is not known")
        }

        case (None, Some(_)) => {
          MainActor.ref ! MainActor.Messages.CheckLastState(project.id)
          SupervisorResult.Change(s"Last state is not known. Requested CheckLastState")
        }

        case (Some(last), Some(desired)) => {
          Scale.isRecent(last.timestamp) match {
            case false => {
              MainActor.ref ! MainActor.Messages.CheckLastState(project.id)
              SupervisorResult.NoChange(s"Last state is too old. Last updated at ${last.timestamp}")
            }
            case true => {
              Deployer(project, last, desired).scale()
            }
          }
        }
      }
    }
  }

  def isRecent(ts: DateTime): Boolean = {
    val now = new DateTime()
    ts.isAfter(now.minusMinutes(MinutesUntilStale))
  }
  
}

