package controllers

import io.flow.common.v0.models.Healthcheck
import io.flow.common.v0.models.json._

import play.api._
import play.api.mvc._
import play.api.libs.json._

@javax.inject.Singleton
class Healthchecks @javax.inject.Inject() (
  @javax.inject.Named("main-actor") mainActor: akka.actor.ActorRef
) extends Controller {

  private val HealthyJson = Json.toJson(Healthcheck(status = "healthy"))

  def getHealthcheck() = Action { request =>
    Ok(HealthyJson)
  }

}
