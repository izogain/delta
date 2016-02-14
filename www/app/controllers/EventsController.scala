package controllers

import io.flow.delta.v0.models.EventType
import io.flow.delta.www.lib.DeltaClientProvider
import io.flow.common.v0.models.User
import io.flow.play.clients.UserTokensClient
import io.flow.play.util.{Pagination, PaginatedCollection}

import play.api._
import play.api.i18n.MessagesApi
import play.api.mvc._

class EventsController @javax.inject.Inject() (
  val messagesApi: MessagesApi,
  override val userTokensClient: UserTokensClient,
  override val deltaClientProvider: DeltaClientProvider
) extends BaseController(userTokensClient, deltaClientProvider) {

  import scala.concurrent.ExecutionContext.Implicits.global

  private[this] val Limit = 100

  override def section = Some(io.flow.delta.www.lib.Section.Events)

  def index(
    page: Int = 0,
    projectId: Option[String],
    `type`: Option[EventType]
  ) = Identified.async { implicit request =>
    for {
      events <- deltaClient(request).events.get(
        projectId = projectId,
        `type` = `type`,
        limit = Limit+1,
        offset = page * Limit
      )
    } yield {
      val title = projectId match {
        case None => "Event Log"
        case Some(id) => s"Event Log: $id"
      }

      Ok(
        views.html.events.index(
          uiData(request).copy(title = Some(title)),
          projectId,
          `type`,
          PaginatedCollection(page, events, Limit)
        )
      )
    }
  }

}
