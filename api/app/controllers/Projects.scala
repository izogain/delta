package controllers

import db.{Authorization, ProjectsDao}
import io.flow.play.clients.UserTokensClient
import io.flow.play.controllers.IdentifiedRestController
import io.flow.play.util.Validation
import io.flow.delta.v0.models.{Project, ProjectForm}
import io.flow.delta.v0.models.json._
import io.flow.common.v0.models.json._
import play.api.mvc._
import play.api.libs.json._

@javax.inject.Singleton
class Projects @javax.inject.Inject() (
  val userTokensClient: UserTokensClient
) extends Controller with IdentifiedRestController with Helpers {

  def get(
    id: Option[String],
    ids: Option[Seq[String]],
    organization: Option[String],
    name: Option[String],
    groupId: _root_.scala.Option[String],
    artifactId: _root_.scala.Option[String],
    version: _root_.scala.Option[String],
    libraryId: _root_.scala.Option[String],
    binary: _root_.scala.Option[String],
    binaryId: _root_.scala.Option[String],
    limit: Long = 25,
    offset: Long = 0
  ) = Identified { request =>
    Ok(
      Json.toJson(
        ProjectsDao.findAll(
          Authorization.User(request.user.id),
          id = id,
          ids = optionals(ids),
          name = name,
          organization = organization,
          groupId = groupId,
          artifactId = artifactId,
          version = version,
          libraryId = libraryId,
          binary = binary,
          binaryId = binaryId,
          limit = limit,
          offset = offset
        )
      )
    )
  }

  def getById(id: String) = Identified { request =>
    withProject(request.user, id) { project =>
      Ok(Json.toJson(project))
    }
  }

  def post() = Identified(parse.json) { request =>
    request.body.validate[ProjectForm] match {
      case e: JsError => {
        UnprocessableEntity(Json.toJson(Validation.invalidJson(e)))
      }
      case s: JsSuccess[ProjectForm] => {
        ProjectsDao.create(request.user, s.get) match {
          case Left(errors) => UnprocessableEntity(Json.toJson(Validation.errors(errors)))
          case Right(project) => Created(Json.toJson(project))
        }
      }
    }
  }

  def putById(id: String) = Identified(parse.json) { request =>
    withProject(request.user, id) { project =>
      request.body.validate[ProjectForm] match {
        case e: JsError => {
          UnprocessableEntity(Json.toJson(Validation.invalidJson(e)))
        }
        case s: JsSuccess[ProjectForm] => {
          ProjectsDao.update(request.user, project, s.get) match {
            case Left(errors) => UnprocessableEntity(Json.toJson(Validation.errors(errors)))
            case Right(updated) => Ok(Json.toJson(updated))
          }
        }
      }
    }
  }

  def deleteById(id: String) = Identified { request =>
    withProject(request.user, id) { project =>
      ProjectsDao.softDelete(request.user, project)
      NoContent
    }
  }

}
