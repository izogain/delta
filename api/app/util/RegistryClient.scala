package util

import io.flow.registry.v0.models.Port
import io.flow.registry.v0.{Authorization, Client}
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success, Try}
import play.api.Play.current
import java.util.concurrent.TimeUnit

object RegistryClient {

  val token = Authorization.Basic("admin") // cheating for now
  val registryApiHost = current.configuration.getString("registry.api.host").get
  val client = new Client(registryApiHost, auth = Some(token))
  val projectPorts = scala.collection.mutable.Map[String, Port]()

  def ports(id: String): Port = {
    upsertProjectPorts(id)
  }

  def upsertProjectPorts(id: String): Port = {
    projectPorts.lift(id).getOrElse {
      Try(
        Await.result(client.applications.getById(id=id),
        Duration(5, TimeUnit.SECONDS))
      ) match {
        case Success(response) => {
          val ports = response.ports.head
          projectPorts += (id -> ports)
          ports
        }
        case Failure(e) => throw e
      }
    }
  }

}
