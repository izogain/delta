package io.flow.delta.api.lib

import io.flow.delta.actors.SupervisorResult
import io.flow.delta.v0.models.State
import org.joda.time.DateTime

/**
  * Represents a difference in the number of instances of a single version of a project.
  */
case class StateDiff(versionName: String, actualInstances: Long, desiredInstances: Long)

object StateDiff {

  /**
    * Compares actual to desired, returning a list of StateDiff
    * records wherever an instance needs to be brought up.
    */
  def up(actual: State, desired: State): Seq[StateDiff] = {
    diff(actual, desired).filter { d => d.desiredInstances > d.actualInstances }
  }

  /**
    * Compares actual to desired, returning a list of StateDiff
    * records wherever an instance needs to be brought down.
    */
  def down(actual: State, desired: State): Seq[StateDiff] = {
    diff(actual, desired).filter { d => d.desiredInstances < d.actualInstances }
  }

  /**
    * Compares actual state to desired state, returning a list of
    * StateDiff objects for any differences. Excludes any versions
    * where actual state is the desired state - e.g. if actual ==
    * desired, you will get back Nil.
    */
  private[this] def diff(actual: State, desired: State): Seq[StateDiff] = {
    desired.versions.flatMap { expVersion =>
      val actualInstances: Long = actual.versions.find { _.name == expVersion.name }.map(_.instances).getOrElse(0)
      actualInstances == expVersion.instances match {
        case true => {
          // Desired number of instances matches actual. Nothing to do
          None
        }
        case false => {
          Some(StateDiff(expVersion.name, actualInstances, expVersion.instances))
        }
      }
    } ++ actual.versions.flatMap { actualVersion =>
      desired.versions.find { _.name == actualVersion.name } match {
        case None => {
          // A version in actual that is not in desired
          Some(StateDiff(actualVersion.name, actualVersion.instances, 0))
        }
        case Some(_) => {
          // Already picked up in earlier loop
          None
        }
      }
    }
  }
  
}
