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
    `type`: Option[EventType],
    hasError: Option[Boolean]
  ) = Identified.async { implicit request =>
    for {
      events <- deltaClient(request).events.get(
        projectId = projectId,
        `type` = `type`,
        hasError = hasError,
        limit = Limit+1,
        offset = page * Limit
      )
    } yield {
      val title = projectId match {
        case None => {
          `type` match {
            case None => "Event Log"
            case Some(t) => s"Event Log: (type $t)"
          }
        }
        case Some(id) => {
          `type` match {
            case None => s"Event Log: $id"
            case Some(t) => s"Event Log: $id (type: $t)"
          }
        }
      }

      Ok(
        views.html.events.index(
          uiData(request).copy(title = Some(title)),
          projectId,
          `type`,
          hasError,
          PaginatedCollection(page, events, Limit)
        )
      )
    }
  }

}
