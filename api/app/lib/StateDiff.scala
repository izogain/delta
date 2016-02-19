package io.flow.delta.api.lib

import io.flow.delta.actors.SupervisorResult
import io.flow.delta.v0.models.State
import org.joda.time.DateTime

/**
  * Represents a difference in the number of instances of a single version of a project.
  */
case class StateDiff(versionName: String, lastInstances: Long, desiredInstances: Long)

object StateDiff {

  /**
    * Compares last to desired, returning a list of StateDiff
    * records wherever an instance needs to be brought up.
    */
  def up(last: State, desired: State): Seq[StateDiff] = {
    diff(last, desired).filter { d => d.desiredInstances > d.lastInstances }
  }

  /**
    * Compares last to desired, returning a list of StateDiff
    * records wherever an instance needs to be brought down.
    */
  def down(last: State, desired: State): Seq[StateDiff] = {
    diff(last, desired).filter { d => d.desiredInstances < d.lastInstances }
  }

  /**
    * Compares last state to desired state, returning a list of
    * StateDiff objects for any differences. Excludes any versions
    * where last state is the desired state - e.g. if last ==
    * desired, you will get back Nil.
    */
  def diff(last: State, desired: State): Seq[StateDiff] = {
    desired.versions.flatMap { expVersion =>
      val lastInstances: Long = last.versions.find { _.name == expVersion.name }.map(_.instances).getOrElse(0)
      lastInstances == expVersion.instances match {
        case true => {
          // Desired number of instances matches last. Nothing to do
          None
        }
        case false => {
          Some(StateDiff(expVersion.name, lastInstances, expVersion.instances))
        }
      }
    } ++ last.versions.flatMap { lastVersion =>
      desired.versions.find { _.name == lastVersion.name } match {
        case None => {
          // A version in last that is not in desired
          Some(StateDiff(lastVersion.name, lastVersion.instances, 0))
        }
        case Some(_) => {
          // Already picked up in earlier loop
          None
        }
      }
    }
  }
  
}
