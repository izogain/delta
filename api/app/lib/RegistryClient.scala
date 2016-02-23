package io.flow.delta.api.lib

import io.flow.play.clients.{Registry, RegistryConstants}
import io.flow.play.util.DefaultConfig
import io.flow.registry.v0.models.Application
import io.flow.registry.v0.{Authorization, Client}
import io.flow.registry.v0.errors.UnitResponse
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

@javax.inject.Singleton
class RegistryClient @javax.inject.Inject() (
  registry: Registry,
  config: DefaultConfig
) extends RegistryConstants {

  lazy val instance = {
      println("registry.host(registry): " + registry.host("registry"))
    new Client(
      registry.host("registry"),
      auth = Some(Authorization.Basic(config.requiredString(TokenVariableName)))
    )
  }

  /**
    * Get an application, turning a 404 into a None
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
