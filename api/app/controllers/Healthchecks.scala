package controllers

import io.flow.common.v0.models.Healthcheck
import io.flow.common.v0.models.json._

import play.api._
import play.api.mvc._
import play.api.libs.json._

class Healthchecks extends Controller {

  private val HealthyJson = Json.toJson(Healthcheck(status = "healthy"))

  def getHealthcheck() = Action { request =>
    // ------------------------------------------------------------
    // Hack just to test if this all works with minimal supervision
    // ------------------------------------------------------------
    import io.flow.delta.actors.MainActor
    MainActor.ref ! MainActor.Messages.Configure("splashpage")
    println("DONE CONFIGURE! SLEEPING FOR 10 SECONDS....")
    Thread.sleep(10000)
    MainActor.ref ! MainActor.Messages.Deploy("flowcommerce/splashpage:0.1.13")
    // ------------------------------------------------------------

    Ok(HealthyJson)
  }

}
