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
    val actual = ProjectLastStatesDao.findByProjectId(Authorization.All, project.id)
    val desired = ProjectDesiredStatesDao.findByProjectId(Authorization.All, project.id)
    Future {
      (actual, desired) match {
        case (Some(act), Some(exp)) => {
          Scale.isRecent(act.timestamp) match {
            case false => {
              MainActor.ref ! MainActor.Messages.CheckLastState(project.id)
              SupervisorResult.NoChange(s"Last state is too old. Last updated at ${act.timestamp}")
            }
            case true => {
              Deployer(project, act, exp).scale()
            }
          }
        }
        case (None, Some(_)) => {
          SupervisorResult.NoChange(s"Last state is not known")
        }
        case (Some(_), None) => {
          SupervisorResult.NoChange(s"Desired state is not known")
        }
        case (None, None) => {
          SupervisorResult.NoChange(s"Last state and desired state are not known")
        }
      }
    }
  }

  def isRecent(ts: DateTime): Boolean = {
    val now = new DateTime()
    ts.isAfter(now.minusMinutes(MinutesUntilStale))
  }
  
}

