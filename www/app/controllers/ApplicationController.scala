package controllers

import io.flow.delta.www.lib.DeltaClientProvider
import io.flow.play.clients.UserTokensClient
import io.flow.play.util.{Pagination, PaginatedCollection}
import play.api._
import play.api.i18n.MessagesApi
import play.api.mvc._

class ApplicationController @javax.inject.Inject() (
  val messagesApi: MessagesApi,
  override val userTokensClient: UserTokensClient,
  override val deltaClientProvider: DeltaClientProvider
) extends BaseController(userTokensClient, deltaClientProvider) {

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
          PaginatedCollection(buildsPage, dashboardBuilds)
        )
      )
    }
  }

}
