package io.flow.delta.actors.functions

import akka.actor.ActorRef
import db.{ProjectLastStatesDao, ProjectDesiredStatesDao}
import io.flow.delta.actors.{MainActor, ProjectActor, SupervisorFunction, SupervisorResult}
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

  private[this] val SecondsUntilStale = (ProjectActor.CheckLastStateIntervalSeconds * 2.5).toInt

  override def run(
    main: ActorRef,
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
          main ! MainActor.Messages.CheckLastState(project.id)
          SupervisorResult.Change(s"Requested CheckLastState as last state is not known")
        }

        case (Some(last), Some(desired)) => {
          Scale.isRecent(last.timestamp) match {
            case false => {
              main ! MainActor.Messages.CheckLastState(project.id)
              SupervisorResult.NoChange(s"Requested CheckLastState as last state is too old[${last.timestamp}]")
            }
            case true => {
              Deployer(project, last, desired).scale(main)
            }
          }
        }
      }
    }
  }

  def isRecent(ts: DateTime): Boolean = {
    val now = new DateTime()
    ts.isAfter(now.minusMinutes(SecondsUntilStale))
  }
  
}

