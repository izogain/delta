package controllers

import db.ItemsDao
import io.flow.play.clients.UserTokensClient
import io.flow.delta.v0.models.json._
import play.api.mvc._
import play.api.libs.json._

@javax.inject.Singleton
class Items @javax.inject.Inject() (
  val userTokensClient: UserTokensClient
) extends Controller with BaseIdentifiedController {

  def get(
    q: Option[String],
    limit: Long = 25,
    offset: Long = 0
  ) = Identified { request =>
    Ok(
      Json.toJson(
        ItemsDao.findAll(
          authorization(request),
          q = q,
          limit = limit,
          offset = offset
        )
      )
    )
  }

}
