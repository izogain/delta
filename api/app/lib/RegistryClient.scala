package io.flow.delta.api.lib

import io.flow.play.util.DefaultConfig
import io.flow.registry.v0.models.Application
import io.flow.registry.v0.{Authorization, Client}
import io.flow.registry.v0.errors.UnitResponse
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext}
import scala.util.{Failure, Success, Try}
import java.util.concurrent.TimeUnit

object RegistryClient {

  lazy val instance = new Client(
    DefaultConfig.requiredString("registry.api.host"),
    auth = Some(Authorization.Basic(DefaultConfig.requiredString("registry.api.token")))
  )

  /**
    * Blocking call to get an application. Turns a 404 into None
    */
  def getById(
    id: String
  ) (
    implicit ec: ExecutionContext
  ): Option[Application] = {
    Try(
      Await.result(instance.applications.getById(id=id),
      Duration(5, TimeUnit.SECONDS))
    ) match {
      case Success(app) => {
        Some(app)
      }
      case Failure(ex) => {
        ex match {
          case UnitResponse(404) => None
          case _: Throwable => throw ex
        }
      }
    }
  }

}
