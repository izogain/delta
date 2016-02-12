package controllers

import io.flow.play.clients.UserTokensClient
import io.flow.play.controllers.IdentifiedRestController
import io.flow.play.util.Validation
import io.flow.delta.v0.models.{Image, ImageForm}
import io.flow.delta.v0.models.json._
import io.flow.common.v0.models.json._
import play.api.mvc._
import play.api.libs.json._

@javax.inject.Singleton
class Images @javax.inject.Inject() (
  val userTokensClient: UserTokensClient
) extends Controller with BaseIdentifiedRestController {

  def get(
    name: Option[String],
    limit: Long = 25,
    offset: Long = 0
  ) = TODO

}
