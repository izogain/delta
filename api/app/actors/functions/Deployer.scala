package io.flow.delta.actors.functions

import io.flow.delta.actors.SupervisorResult
import io.flow.delta.api.lib.{StateDiff, StateFormatter}
import io.flow.delta.lib.Text
import io.flow.delta.v0.models.{Project, State}
import org.joda.time.DateTime

case class Deployer(project: Project, actual: State, expected: State) {

  /**
    * Scales up or down the project instances to move actual state
    * towards expected state. This works in two phases:
    * 
    *   1. Find any instances that need to be brought up, and issue
    *      messages to bring those instances up.
    *   2. Only if ALL instances are UP, find instances that need
    *      to be brought down, and fire events to bring those down.
    * 
    * This ordering attempts to minimize the case that we bring down
    * the last instances which in turn leaves a service offline or
    * unhealthy.
    */
  def scale(): SupervisorResult = {
    StateDiff.up(actual, expected).toList match {
      case Nil => {
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
            execute(diffs)
            SupervisorResult.Change(s"Scale Down: " + toLabel(diffs))
          }
        }
      }
      case diffs => {
        execute(diffs)
        SupervisorResult.Change(s"Scale Up: " + toLabel(diffs))
      }
    }
  }

  private[this] def execute(diffs: Seq[StateDiff]) {
    assert(!diffs.isEmpty, "Must have at least one state diff")
    diffs.foreach { diff =>
      if (diff.actualInstances > diff.expectedInstances) {
        val instances = diff.actualInstances - diff.expectedInstances
        println(s"Bring down ${Text.pluralize(instances, "instance", "instances")}  instances of ${diff.versionName}")
      } else if (diff.actualInstances < diff.expectedInstances) {
        val instances = diff.expectedInstances - diff.actualInstances
        println(s"Bring up ${Text.pluralize(instances, "instance", "instances")}  instances of ${diff.versionName}")
      }
    }
  }

  private[this] def toLabel(diffs: Seq[StateDiff]): String = {
    diffs.flatMap { diff =>
      if (diff.actualInstances > diff.expectedInstances) {
        val label = Text.pluralize(diff.actualInstances - diff.expectedInstances, "instance", "instances")
        Some(s"${diff.versionName}: Remove $label")
      } else if (diff.actualInstances < diff.expectedInstances) {
        val label = Text.pluralize(diff.expectedInstances - diff.actualInstances , "instance", "instances")
        Some(s"${diff.versionName}: Add $label")
      } else {
        None
      }
    }.mkString(", ")
  }
}
