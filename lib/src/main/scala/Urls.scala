package io.flow.delta.lib

import io.flow.play.util.DefaultConfig
import io.flow.delta.v0.models.{ItemSummary, ItemSummaryUndefinedType, ProjectSummary}

/**
 * All our URLs to the webapp go here. We tried to use the www routers
 * directly as a separate project in the build, but caused problems in
 * the compile step (every other compile step failed). Instead we
 * provide hard coded urls - but keep in one file for easier
 * maintenance.
 */
case class Urls(
  wwwHost: String = DefaultConfig.requiredString("delta.www.host")
) {

  def project(id: String) = s"/projects/$id"

  def subscriptions(userIdentifier: Option[String]): String = {
    val base = "/subscriptions/"
    userIdentifier match {
      case None => base
      case Some(id) => {
        val encoded = play.utils.UriEncoding.encodePathSegment(id, "UTF-8")
        s"$base$encoded"
      }
    }
  }

  def www(rest: play.api.mvc.Call): String = {
    www(rest.toString)
  }

  def www(rest: String): String = {
    s"$wwwHost$rest"
  }

  def itemSummary(summary: ItemSummary): String = {
    summary match {
      case ProjectSummary(id, org, name, url) => project(id)
      case ItemSummaryUndefinedType(name) => "#"
    }
  }

}
