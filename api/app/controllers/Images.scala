package controllers

import db.{ImagesDao, ImagesWriteDao}
import io.flow.common.v0.models.User
import io.flow.delta.v0.models.Image
import io.flow.delta.v0.models.json._
import io.flow.play.clients.UserTokensClient
import io.flow.play.controllers.IdentifiedRestController
import io.flow.play.util.Validation
import io.flow.postgresql.Authorization
import play.api.mvc._
import play.api.libs.json._

@javax.inject.Singleton
class Images @javax.inject.Inject() (
  val userTokensClient: UserTokensClient,
  imagesWriteDao: ImagesWriteDao
) extends Controller with BaseIdentifiedRestController {

  def get(
    id: Option[Seq[String]],
    project: Option[String],
    name: Option[String],
    limit: Long,
    offset: Long,
    sort: String
  ) = Identified { request =>
    withOrderBy(sort) { orderBy =>
      Ok(
        Json.toJson(
          ImagesDao.findAll(
            ids = optionals(id),
            projectId = project,
            names = name.map { n => Seq(n) },
            limit = limit,
            offset = offset,
            orderBy = orderBy
          )
        )
      )
    }
  }

  def getById(id: String) = Identified { request =>
    withImage(request.user, id) { image =>
      Ok(Json.toJson(image))
    }
  }

  def deleteById(id: String) = Identified { request =>
    withImage(request.user, id) { image =>
      imagesWriteDao.delete(request.user, image)
      NoContent
    }
  }

  def withImage(user: User, id: String)(
    f: Image => Result
  ): Result = {
    ImagesDao.findById(id) match {
      case None => {
        Results.NotFound
      }
      case Some(image) => {
        f(image)
      }
    }
  }

}
