package controllers

import db.{ProjectsDao, ProjectDesiredStatesDao, ProjectLastStatesDao, SettingsDao}
import io.flow.postgresql.Authorization
import io.flow.delta.actors.MainActor
import io.flow.delta.v0.models.{Project, ProjectForm, ProjectState, SettingsForm}
import io.flow.delta.v0.models.json._
import io.flow.play.clients.UserTokensClient
import io.flow.play.controllers.IdentifiedRestController
import io.flow.play.util.Validation
import io.flow.common.v0.models.json._
import play.api.mvc._
import play.api.libs.json._

@javax.inject.Singleton
class Projects @javax.inject.Inject() (
  val userTokensClient: UserTokensClient
) extends Controller with BaseIdentifiedRestController {

  def get(
    id: Option[Seq[String]],
    organization: Option[String],
    name: Option[String],
    limit: Long,
    offset: Long,
    sort: String
  ) = Identified { request =>
    withOrderBy(sort) { orderBy =>
      Ok(
        Json.toJson(
          ProjectsDao.findAll(
            authorization(request),
            ids = optionals(id),
            name = name,
            organizationId = organization,
            limit = limit,
            offset = offset,
            orderBy = orderBy
          )
        )
      )
    }
  }

  def getById(id: String) = Identified { request =>
    withProject(request.user, id) { project =>
      Ok(Json.toJson(project))
    }
  }

  def post() = Identified { request =>
    JsValue.sync(request.contentType, request.body) { js =>
      js.validate[ProjectForm] match {
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
  }

  def putById(id: String) = Identified { request =>
    withProject(request.user, id) { project =>
      JsValue.sync(request.contentType, request.body) { js =>
        js.validate[ProjectForm] match {
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
  }

  def deleteById(id: String) = Identified { request =>
    withProject(request.user, id) { project =>
      ProjectsDao.delete(request.user, project)
      NoContent
    }
  }
 
  def getSettingsById(id: String) = Identified { request =>
    withProject(request.user, id) { project =>
      Ok(
        Json.toJson(
          SettingsDao.findByProjectIdOrDefault(Authorization.User(request.user.id), project.id)
        )
      )
    }
  }

  def putSettingsById(id: String) = Identified { request =>
    withProject(request.user, id) { project =>
      JsValue.sync(request.contentType, request.body) { js =>
        js.validate[SettingsForm] match {
          case e: JsError => {
            UnprocessableEntity(Json.toJson(Validation.invalidJson(e)))
          }
          case s: JsSuccess[SettingsForm] => {
            Ok(
              Json.toJson(
                SettingsDao.upsert(request.user, project.id, s.get)
              )
            )
          }
        }
      }
    }
  }

  def getStateAndLatestById(id: String) = Identified { request =>
    withProject(request.user, id) { project =>
      Ok(
        Json.toJson(
          ProjectState(
            desired = ProjectDesiredStatesDao.findByProjectId(authorization(request), id),
            last = ProjectLastStatesDao.findByProjectId(authorization(request), id)
          )
        )
      )
    }
  }

  def postEventsAndPursueDesiredStateById(id: String) = Identified { request =>
    withProject(request.user, id) { project =>
      MainActor.ref ! MainActor.Messages.ProjectSync(project.id)
      NoContent
    }
  }

  def getStateAndDesiredById(id: String) = TODO

  def postStateAndDesiredById(id: String) = TODO

  def getStateAndLastById(id: String) = TODO

}
