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
  * scale up or down in production. Scale Up will always happen first;
  * scale down only initiatied after Scale Up is complete.
  */
object Scale extends SupervisorFunction {

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
              SupervisorResult.NoChange(s"Actual state is too old. Last updated at ${act.timestamp}")
            }
            case true => {
              val deployer = Deployer(project, act, exp)
              deployer.up() match {
                case SupervisorResult.NoChange(desc) => deployer.down()
                case r: SupervisorResult.Change => r
                case r: SupervisorResult.Error => r
              }
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

  override def isEnabled(settings: Settings) = settings.scale

}

