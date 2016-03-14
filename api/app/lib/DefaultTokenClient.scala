package io.flow.delta.api.lib

import db.{TokensDao, UsersDao}
import io.flow.common.v0.models.UserReference
import io.flow.token.v0.interfaces.Client
import io.flow.token.v0.errors.UnitResponse
import io.flow.token.v0.models.{ Token => FlowToken }
import scala.concurrent.Future

@javax.inject.Singleton
class DefaultTokenClient() extends Client {

  def baseUrl = throw new UnsupportedOperationException()

  def healthchecks: io.flow.token.v0.Healthchecks = throw new UnsupportedOperationException()

  def tokens: io.flow.token.v0.Tokens = new Tokens()

}

class Tokens() extends io.flow.token.v0.Tokens {

  def get(
   tokens: Seq[String]
  )(implicit ec: scala.concurrent.ExecutionContext): scala.concurrent.Future[Seq[io.flow.token.v0.models.Token]] = Future {
    tokens.flatMap { t =>
      TokensDao.findByToken(t) match {
        case Some(t) => {
          Some(FlowToken(user = UserReference(t.user.id)))
        }

        case None => {
          UsersDao.findById(t).map { u => FlowToken(user = UserReference(id = u.id)) }
        }
      }
    }
  }

  def getByToken(
    token: String
  )(implicit ec: scala.concurrent.ExecutionContext): scala.concurrent.Future[io.flow.token.v0.models.Token] = {
    get(Seq(token)).map(_.headOption.getOrElse {
      throw new UnitResponse(404)
    })
  }

  def post(
    tokenForm: io.flow.token.v0.models.TokenForm
  )(implicit ec: scala.concurrent.ExecutionContext) = throw new UnsupportedOperationException()

  def deleteByToken(
   token: String
  )(implicit ec: scala.concurrent.ExecutionContext) = throw new UnsupportedOperationException()

}