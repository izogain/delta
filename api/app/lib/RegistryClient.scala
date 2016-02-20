package io.flow.delta.api.lib

import io.flow.play.clients.Registry
import io.flow.play.util.DefaultConfig
import io.flow.registry.v0.models.Application
import io.flow.registry.v0.{Authorization, Client}
import io.flow.registry.v0.errors.UnitResponse
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

@javax.inject.Singleton
class RegistryClient @javax.inject.Inject() (registry: Registry) {

  lazy val instance = registry.withHostAndToken("registry") { (host, token) =>
    new Client(host, auth = Some(Authorization.Basic(token)))
  }

  /**
    * Blocking call to get an application. Turns a 404 into None
    */
  def getById(
    id: String
  ) (
    implicit ec: ExecutionContext
  ): Future[Option[Application]] = {
    instance.applications.getById(id=id).map { application =>
      Some(application)
    }.recover {
      case UnitResponse(404) => None
      case ex: Throwable => throw ex
    }
  }

}
