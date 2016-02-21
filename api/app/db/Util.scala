package db

import io.flow.delta.api.lib.Semver
import io.flow.delta.v0.models.BuildForm
import io.flow.play.util.UrlKey

object Util {

  val DefaultBuildFormName = "root"

  private[this] val urlKey = UrlKey(minKeyLength = 3)

  def dockerfilePathToBuildForm(projectId: String, path: String): BuildForm = {
    val name = path.split("/").dropRight(1).filter(!_.startsWith(".")).toList match {
      case Nil => DefaultBuildFormName
      case multiple => multiple.mkString("-")
    }

    BuildForm(
      projectId = projectId,
      name = urlKey.generate(name),
      dockerfilePath = path
    )
  }

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
