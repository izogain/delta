package io.flow.delta.api.lib

import io.flow.delta.lib.Text
import io.flow.delta.v0.models.Version

object StateFormatter {

  def label(versions: Seq[Version]): String = {
    versions.
      sortBy { v =>
        Semver.parse(v.name) match {
          case None => s"9:$v"
          case Some(tag) => s"1:${tag.sortKey}"
        }
      }.
      map { v =>
        val label = Text.pluralize(v.instances, "instance", "instances")
        s"${v.name}: $label"
      }.mkString(", ")
  }

}
