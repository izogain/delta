package controllers

import db.ShasDao
import io.flow.common.v0.models.User
import io.flow.delta.v0.models.Sha
import io.flow.delta.v0.models.json._
import io.flow.play.clients.UserTokensClient
import io.flow.play.controllers.IdentifiedRestController
import io.flow.play.util.Validation
import io.flow.postgresql.Authorization
import play.api.mvc._
import play.api.libs.json._

@javax.inject.Singleton
class Shas @javax.inject.Inject() (
  val userTokensClient: UserTokensClient
) extends Controller with BaseIdentifiedRestController {

  def get(
    id: Option[Seq[String]],
    project: Option[String],
    branch: Option[String],
    hash: Option[String],
    limit: Long,
    offset: Long,
    sort: String
  ) = Identified { request =>
    withOrderBy(sort) { orderBy =>
      Ok(
        Json.toJson(
          ShasDao.findAll(
            authorization(request),
            ids = optionals(id),
            projectId = project,
            branch = branch,
            hash = hash,
            limit = limit,
            offset = offset,
            orderBy = orderBy
          )
        )
      )
    }
  }

  def getById(id: String) = Identified { request =>
    withSha(request.user, id) { sha =>
      Ok(Json.toJson(sha))
    }
  }

  def deleteById(id: String) = Identified { request =>
    withSha(request.user, id) { sha =>
      ShasDao.delete(request.user, sha)
      NoContent
    }
  }

  def withSha(user: User, id: String)(
    f: Sha => Result
  ): Result = {
    ShasDao.findById(Authorization.User(user.id), id) match {
      case None => {
        Results.NotFound
      }
      case Some(sha) => {
        f(sha)
      }
    }
  }

  
}
