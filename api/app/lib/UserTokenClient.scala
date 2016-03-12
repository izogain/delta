package io.flow.delta.api.lib

import db.UsersDao
import io.flow.play.clients.UserTokensClient
import io.flow.common.v0.models.UserReference
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

@javax.inject.Singleton
class DefaultUserTokensClient() extends UserTokensClient {

  override def getUserByToken(
    token: String
  )(implicit ec: ExecutionContext): Future[Option[UserReference]] = {
    // token is either the user id or a user token
    Future {
      UsersDao.findById(token) match {
        case None => UsersDao.findByToken(token)
        case Some(u) => Some(u)
      }
    }
  }

}
