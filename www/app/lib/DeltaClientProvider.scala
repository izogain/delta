package io.flow.delta.www.lib

import io.flow.common.v0.models.UserReference
import io.flow.delta.v0.{Authorization, Client}
import io.flow.play.util
import io.flow.play.util.AuthHeaders
import io.flow.token.v0.Tokens
import play.api.libs.ws.WSClient

trait DeltaClientProvider extends io.flow.token.v0.interfaces.Client {

  def newClient(user: Option[UserReference], requestId: Option[String]): Client

}

@javax.inject.Singleton
class DefaultDeltaClientProvider @javax.inject.Inject() (
  config: util.Config,
  wSClient: WSClient,
  authHeaders: AuthHeaders
) extends DeltaClientProvider {

  private[this] val host = config.requiredString("delta.api.host")

  private[this] lazy val anonymousClient = new Client(ws = wSClient, host)

  override def newClient(user: Option[UserReference], requestId: Option[String]): Client = {
    user match {
      case None => {
        anonymousClient
      }
      case Some(u) => {
        val authHeaderUser = requestId match {
          case Some(rid) => AuthHeaders.user(user = u, requestId = rid)
          case None => AuthHeaders.user(user = u)
        }

        new Client(
          ws = wSClient,
          baseUrl = host,
          auth = Some(
            Authorization.Basic(
              username = u.id.toString,
              password = None
            )
          ),
          defaultHeaders = authHeaders.headers(authHeaderUser)
        )
      }
    }
  }

  override def baseUrl: String = throw new UnsupportedOperationException()

  override def tokens: Tokens = throw new UnsupportedOperationException()

  override def tokenValidations = throw new UnsupportedOperationException()

  override def organizationTokens = throw new UnsupportedOperationException()

  override def partnerTokens = throw new UnsupportedOperationException()
}
