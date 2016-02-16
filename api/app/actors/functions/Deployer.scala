package io.flow.delta.actors.functions

import io.flow.delta.api.lib.{StateDiff, StateFormatter}
import io.flow.delta.actors.SupervisorResult
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

  def up(): SupervisorResult = {
    StateDiff.up(actual, expected).toList match {
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
          s"Bring Up: " + diffs.map { diff => s"${diff.versionName}: Add ${diff.expectedInstances - diff.actualInstances} instance(s)" }.mkString(", ")
        )
      }
    }
  }

  def down(): SupervisorResult = {
    StateDiff.down(actual, expected).toList match {
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
          s"Bring Down: " + diffs.map { diff => s"${diff.versionName}: Add ${diff.actualInstances - diff.expectedInstances} instance(s)" }.mkString(", ")
        )
      }
    }
  }

}
