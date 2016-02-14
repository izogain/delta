package controllers

import db.EventsDao
import io.flow.delta.v0.models.{Event, EventType}
import io.flow.delta.v0.models.json._
import io.flow.play.clients.UserTokensClient
import io.flow.play.controllers.IdentifiedRestController
import io.flow.play.util.Validation
import play.api.mvc._
import play.api.libs.json._

@javax.inject.Singleton
class Events @javax.inject.Inject() (
  val userTokensClient: UserTokensClient
) extends Controller with BaseIdentifiedRestController {

  def get(
    id: Option[Seq[String]],
    project: Option[String],
    `type`: Option[EventType],
    limit: Long,
    offset: Long,
    sort: String
  ) = Identified { request =>
    withOrderBy(sort) { orderBy =>
      Ok(
        Json.toJson(
          EventsDao.findAll(
            ids = optionals(id),
            projectId = project,
            `type` = `type`,
            limit = limit,
            offset = offset,
            orderBy = orderBy
          )
        )
      )
    }
  }

  def getById(id: String) = Identified { request =>
    withEvent(id) { event =>
      Ok(Json.toJson(event))
    }
  }

  def withEvent(id: String)(
    f: Event => Result
  ): Result = {
    EventsDao.findById(id) match {
      case None => {
        Results.NotFound
      }
      case Some(event) => {
        f(event)
      }
    }
  }

}  
