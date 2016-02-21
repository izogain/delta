package controllers

import db.{BuildsDao, ImagesDao, ProjectsDao,ProjectsWriteDao, BuildDesiredStatesDao, BuildLastStatesDao, SettingsDao}
import io.flow.postgresql.Authorization
import io.flow.common.v0.models.User
import io.flow.delta.actors.MainActor
import io.flow.delta.v0.models.{Build, Project, ProjectForm, BuildState, SettingsForm}
import io.flow.delta.v0.models.json._
import io.flow.play.clients.UserTokensClient
import io.flow.play.controllers.IdentifiedRestController
import io.flow.play.util.Validation
import io.flow.common.v0.models.json._
import play.api.mvc._
import play.api.libs.json._

@javax.inject.Singleton
class Projects @javax.inject.Inject() (
  val userTokensClient: UserTokensClient,
  @javax.inject.Named("main-actor") mainActor: akka.actor.ActorRef,
  projectsWriteDao: ProjectsWriteDao
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
          // TODO: val dockerfiles = getDockerfilesFromScms(request.user, form.scms, form.uri)
          val dockerfiles = if (s.get.name == "delta") {
            Seq("/Dockerfile.api", "/Dockerfile.www")
          } else {
            Seq("/Dockerfile")
          }

          projectsWriteDao.create(request.user, s.get, dockerfiles) match {
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
            projectsWriteDao.update(request.user, project, s.get) match {
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
      projectsWriteDao.delete(request.user, project)
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

  def getBuildsAndStatesById(id: String) = Identified { request =>
    withProject(request.user, id) { project =>
      Ok(
        Json.toJson(
          BuildsDao.findAllByProjectId(authorization(request), project.id).map { build =>
            BuildState(
              name = build.name,
              desired = BuildDesiredStatesDao.findByBuildId(authorization(request), build.id),
              last = BuildLastStatesDao.findByBuildId(authorization(request), build.id),
              latestImage = ImagesDao.findAll(buildId = Some(build.id)).headOption.map( i => s"${i.name}:${i.version}" )
            )
          }.toSeq
        )
      )
    }
  }

  def postEventsAndPursueDesiredStateById(id: String) = Identified { request =>
    withProject(request.user, id) { project =>
      mainActor ! MainActor.Messages.ProjectSync(project.id)
      NoContent
    }
  }

  def getBuildsAndStatesAndDesiredByIdAndBuildName(id: String, buildName: String) = TODO

  def postBuildsAndStatesAndDesiredByIdAndBuildName(id: String, buildName: String) = TODO

  def getBuildsAndStatesAndLastByIdAndBuildName(id: String, buildName: String) = TODO

  def withBuild(user: User, projectId: String, name: String)(
    f: Build => Result
  ): Result = {
    BuildsDao.findByProjectIdAndName(Authorization.User(user.id), projectId, name) match {
      case None => {
        Results.NotFound
      }
      case Some(build) => {
        f(build)
      }
    }
  }

}
