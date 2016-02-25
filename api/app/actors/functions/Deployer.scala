package io.flow.delta.actors.functions

import io.flow.delta.actors.{MainActor, MainActorProvider, SupervisorResult}
import io.flow.delta.api.lib.{StateDiff, StateFormatter}
import io.flow.delta.lib.Text
import io.flow.delta.v0.models.{Build, State}
import org.joda.time.DateTime

case class Deployer(build: Build, last: State, desired: State) {

  /**
    * Scales up or down the build instances to move last state
    * towards desired state. This works in two phases:
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
    StateDiff.up(last.versions, desired.versions).toList match {
      case Nil => {
        StateDiff.down(last.versions, desired.versions).toList match {
          case Nil => {
            SupervisorResult.Ready(
              s"Last state[%s] matches desired state[%s]".format(
                StateFormatter.label(last.versions),
                StateFormatter.label(desired.versions)
              )
            )
          }
          case diffs => {
            MainActorProvider.ref() ! MainActor.Messages.Scale(build.id, diffs)
            SupervisorResult.Change(s"Scale Down: " + toLabel(diffs))
          }
        }
      }
      case diffs => {
        MainActorProvider.ref() ! MainActor.Messages.Scale(build.id, diffs)
        SupervisorResult.Change(s"Scale Up: " + toLabel(diffs))
      }
    }
  }

  private[this] def toLabel(diffs: Seq[StateDiff]): String = {
    diffs.flatMap { diff =>
      if (diff.lastInstances > diff.desiredInstances) {
        val label = Text.pluralize(diff.lastInstances - diff.desiredInstances, "instance", "instances")
        Some(s"${diff.versionName}: Remove $label")
      } else if (diff.lastInstances < diff.desiredInstances) {
        val label = Text.pluralize(diff.desiredInstances - diff.lastInstances , "instance", "instances")
        Some(s"${diff.versionName}: Add $label")
      } else {
        None
      }
    }.mkString(", ")
  }
}
