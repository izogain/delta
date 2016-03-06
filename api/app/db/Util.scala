package db

import io.flow.delta.lib.Semver

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

  /**
    * Generates a lexicographic sort key for the specified version. If
    * semver, versions numbers will sort in ascending order. All
    * non-semver labels sort first by their name (so we can rely on
    * the last tag in a list being the largest).
    * 
    * The sort key has a prefix digit of 3, 6 - these are chosen to
    * have gaps between them to slot in other sort key over time if
    * needed
    * 
    * @param name The version name - e.g. 0.0.1
    */
  def generateVersionSortKey(name: String): String = {
    Semver.parse(name) match {
      case None => s"3:$name"  // Place earlier. We want the last tag to be the latest
      case Some(semver) => s"6:${semver.sortKey}"
    }
  }
  
}
