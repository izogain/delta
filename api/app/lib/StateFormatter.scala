package io.flow.delta.api.lib

import io.flow.delta.lib.Text
import io.flow.delta.v0.models.Version

object StateFormatter {

  def label(versions: Seq[Version]): String = {
    versions.map { v =>
      val label = Text.pluralize(v.instances, "instance", "instances")
      s"${v.name}: $label"
    }.mkString(", ")
  }

}
