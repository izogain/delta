package controllers

import io.flow.delta.v0.models.Version
import io.flow.delta.www.lib.DeltaClientProvider
import io.flow.play.util.{Pagination, PaginatedCollection}
import org.joda.time.DateTime
import play.api.i18n.MessagesApi
import play.api.mvc._

/**
  * Wrapper to simplify display
  */
case class BuildView(val dashboardBuild: io.flow.delta.v0.models.DashboardBuild) {

  private[this] val MinutesUntilError = 30

  private[this] val last = format(dashboardBuild.last.versions)
  private[this] val desired = format(dashboardBuild.desired.versions)

  val status: Option[String] = {
    last == desired match {
      case true => None
      case false => {
        Some(
          if (dashboardBuild.desired.timestamp.isBefore(new DateTime().minusMinutes(MinutesUntilError))) {
            "danger"
          } else {
            "warning"
          }
        )
      }
    }
  }

  val label = {
    last == desired match {
      case true => {
        s"Running $desired"
      }
      case false => {
        s"Transitioning from $last to $desired"
      }
    }

  }

  def format(versions: Seq[Version]): String = {
    versions.map(_.name) match {
      case Nil => "Nothing" 
      case names => names.mkString(", ")
    }
  }

}

class ApplicationController @javax.inject.Inject() (
  val messagesApi: MessagesApi,
  override val tokenClient: io.flow.token.v0.interfaces.Client,
  override val deltaClientProvider: DeltaClientProvider
) extends BaseController(tokenClient, deltaClientProvider) {

  import scala.concurrent.ExecutionContext.Implicits.global
 
  override def section = Some(io.flow.delta.www.lib.Section.Dashboard)

  def redirect = Action { request =>
    Redirect(request.path + "/")
  }

  def index(organization: Option[String], buildsPage: Int = 0) = Identified.async { implicit request =>
    for {
      dashboardBuilds <- deltaClient(request).dashboardBuilds.get(
        limit = Pagination.DefaultLimit+1,
        offset = buildsPage * Pagination.DefaultLimit
      )
    } yield {
      Ok(
        views.html.index(
          uiData(request).copy(organization = organization),
          PaginatedCollection(buildsPage, dashboardBuilds.map(BuildView(_)))
        )
      )
    }
  }

}
