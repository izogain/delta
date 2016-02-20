package io.flow.delta.www.lib

import io.flow.play.clients.{Registry, UserTokensClient}
import io.flow.play.util.Config
import io.flow.common.v0.models.User
import io.flow.delta.v0.{Authorization, Client}
import io.flow.delta.v0.errors.UnitResponse
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

trait DeltaClientProvider extends UserTokensClient {

  def newClient(user: Option[User]): Client

}

@javax.inject.Singleton
class DefaultDeltaClientProvider @javax.inject.Inject() (
  config: Config
) extends DeltaClientProvider {

  private[this] val host = config.requiredString("delta.api.host")

  private[this] lazy val anonymousClient = new Client(host)

  override def newClient(user: Option[User]): Client = {
    user match {
      case None => {
        anonymousClient
      }
      case Some(u) => {
        new Client(
          apiUrl = host,
          auth = Some(
            Authorization.Basic(
              username = u.id.toString,
              password = None
            )
          )
        )
      }
    }
  }

  override def getUserByToken(
    token: String
  )(implicit ec: ExecutionContext): Future[Option[User]] = {
    // Token is just the ID
    anonymousClient.users.get(id = Some(token)).map { _.headOption }
  }

}
