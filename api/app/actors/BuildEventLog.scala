package io.flow.delta.actors

import io.flow.delta.lib.BuildNames
import io.flow.delta.v0.models.Build

trait BuildEventLog extends EventLog {

  override def logPrefix: String = {
    val base = format(this)
    withBuild { build =>
      s"$base[${BuildNames.projectName(build)}]"
    }.getOrElse {
      s"$base[unknown build]"
    }
  }

  /**
    * Event log relies on a build; this method can be provided by mixing in WithBuild
    **/
  def withBuild[T](f: Build => T): Option[T]

}
