package io.flow.delta.api.lib

import io.flow.play.util.DefaultConfig
import io.flow.registry.v0.models.Application
import io.flow.registry.v0.{Authorization, Client}
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success, Try}
import play.api.Play.current
import java.util.concurrent.TimeUnit

object RegistryClient {

  lazy val client = new Client(
    DefaultConfig.requiredString("registry.api.host"),
    auth = Some(Authorization.Basic("admin"))
  )

  def getById(id: String): Application = {
    Try(
      Await.result(client.applications.getById(id=id),
      Duration(5, TimeUnit.SECONDS))
    ) match {
      case Success(response) => response
      case Failure(e) => throw e
    }
  }

}
