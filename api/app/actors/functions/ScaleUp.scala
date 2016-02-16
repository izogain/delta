package io.flow.delta.actors.functions

import db.{ProjectActualStatesDao, ProjectExpectedStatesDao}
import io.flow.delta.actors.{SupervisorFunction, SupervisorResult}
import io.flow.postgresql.Authorization
import io.flow.delta.v0.models.{Project, Settings}
import org.joda.time.DateTime
import scala.concurrent.Future

/**
  * If we have both an expected state and a recent actual state,
  * compares the two to see if there are any instances we need to
  * bring up in production. If so, deploys those versions.
  */
object ScaleUp extends SupervisorFunction {

  override def run(
    project: Project
  ) (
    implicit ec: scala.concurrent.ExecutionContext
  ): Future[SupervisorResult] = {
    val actual = ProjectActualStatesDao.findByProjectId(Authorization.All, project.id)
    val expected = ProjectExpectedStatesDao.findByProjectId(Authorization.All, project.id)
    Future {
      (actual, expected) match {
        case (Some(act), Some(exp)) => {
          Deployer.isRecent(act) match {
            case false => {
              SupervisorResult.NoChange(s"Actual state last updated at ${act.timestamp} is not recent")
            }
            case true => {
              Deployer(project, act, exp).up()
            }
          }
        }
        case (None, Some(_)) => {
          SupervisorResult.NoChange(s"Actual state is not known")
        }
        case (Some(_), None) => {
          SupervisorResult.NoChange(s"Expected state is not known")
        }
        case (None, None) => {
          SupervisorResult.NoChange(s"Actual state and expected state are not known")
        }
      }
    }
  }

  override def isEnabled(settings: Settings) = true // TODO: settings.scaleUp

}

