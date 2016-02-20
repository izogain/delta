package db

import io.flow.delta.api.lib.Semver

object Util {

  def trimmedString(value: Option[String]): Option[String] = {
    value match {
      case None => None
      case Some(v) => {
        v.trim match {
          case "" => None
          case trimmed => Some(trimmed)
        }
      }
    }
  }

  def generateVersionSortKey(name: String): String = {
    Semver.parse(name) match {
      case None => s"1:$name"  // Place earlier. We want the last tag to be the latest
      case Some(semver) => s"9:${semver.sortKey}"
    }
  }
  
}
