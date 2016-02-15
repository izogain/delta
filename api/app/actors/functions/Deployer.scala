package io.flow.delta.actors.functions

import io.flow.delta.actors.SupervisorResult
import io.flow.delta.api.lib.StateFormatter
import io.flow.delta.v0.models.{Project, State}
import org.joda.time.DateTime

object Deployer {

  val MinutesUntilState = 10

  def isRecent(state: State): Boolean = {
    val now = new DateTime()
    state.timestamp.isAfter(now.minusMinutes(MinutesUntilState))
  }

}

case class Deployer(project: Project, actual: State, expected: State) {

  private[this] case class Diff(versionName: String, expectedInstances: Long, actualInstances: Long)

  def up(): SupervisorResult = {
    val diff = expected.versions.flatMap { expVersion =>
      val actualInstances: Long = actual.versions.find { _.name == expVersion.name }.map(_.instances).getOrElse(0)
      actualInstances < expVersion.instances match {
        case true => {
          // Expected number of instances matches actual. Nothing to do
          None
        }
        case false => {
          Some(Diff(expVersion.name, expVersion.instances, actualInstances))
        }
      }
    }

    diff.toList match {
      case Nil => {
        SupervisorResult.NoChange(
          s"Actual state[%s] matches expected state[%s]".format(
            StateFormatter.label(actual.versions),
            StateFormatter.label(expected.versions)
          )
        )
      }
      case diffs => {
        SupervisorResult.Change(
          s"Deploy: " + diffs.map { diff => s"${diff.versionName}: Add ${diff.expectedInstances - diff.actualInstances} instance(s)" }.mkString(", ")
        )
      }
    }
  }

}
