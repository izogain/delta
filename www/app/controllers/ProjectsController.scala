package controllers

import io.flow.delta.v0.errors.UnitResponse
import io.flow.delta.v0.models.{EventType, Organization, Project, ProjectForm, Scms, SettingsForm, Visibility}
import io.flow.delta.www.lib.DeltaClientProvider
import io.flow.common.v0.models.User
import io.flow.play.clients.UserTokensClient
import io.flow.play.util.{Pagination, PaginatedCollection}
import scala.concurrent.Future

import play.api._
import play.api.i18n.MessagesApi
import play.api.mvc._
import play.api.data._
import play.api.data.Forms._

class ProjectsController @javax.inject.Inject() (
  val messagesApi: MessagesApi,
  override val userTokensClient: UserTokensClient,
  override val deltaClientProvider: DeltaClientProvider
) extends BaseController(userTokensClient, deltaClientProvider) {

  import scala.concurrent.ExecutionContext.Implicits.global

  override def section = Some(io.flow.delta.www.lib.Section.Projects)

  def index(page: Int = 0) = Identified.async { implicit request =>
    for {
      projects <- deltaClient(request).projects.get(
        limit = Pagination.DefaultLimit+1,
        offset = page * Pagination.DefaultLimit
      )
    } yield {
      Ok(
        views.html.projects.index(
          uiData(request),
          PaginatedCollection(page, projects)
        )
      )
    }
  }

  def show(id: String, eventsPage: Int) = Identified.async { implicit request =>
    withProject(request, id) { project =>
      for {
        settings <- deltaClient(request).projects.getSettingsById(id)
        state <- deltaClient(request).projects.getStateAndLatestById(id)
        events <- deltaClient(request).events.get(
          projectId = Some(project.id),
          `type` = Some(EventType.Change),
          limit = Pagination.DefaultLimit+1,
          offset = eventsPage * Pagination.DefaultLimit
        )
        tags <- deltaClient(request).tags.get(
          projectId = Some(id),
          sort = "-tags.created_at",
          limit = 1
        )
        shas <- deltaClient(request).shas.get(
          projectId = Some(id),
          branch = Some("master"),
          limit = 1
        )
      } yield {
        Ok(
          views.html.projects.show(
            uiData(request),
            project,
            state,
            settings,
            shas.headOption.map(_.hash),
            tags.headOption,
            PaginatedCollection(eventsPage, events)
          )
        )
      }
    }
  }

  def github() = Identified.async { implicit request =>
    for {
      orgs <- organizations(request)
    } yield {
      orgs match {
        case Nil => {
          Redirect(routes.OrganizationsController.create(return_url = Some(request.path))).
            flashing("warning" -> "Add a new org before adding a project")
        }
        case one :: Nil => {
          Redirect(routes.ProjectsController.githubOrg(one.id))
        }
        case multiple => {
          Ok(
            views.html.projects.github(
              uiData(request), multiple
            )
          )
        }
      }
    }
  }

  def githubOrg(orgId: String, repositoriesPage: Int = 0) = Identified.async { implicit request =>
    withOrganization(request, orgId) { org =>
      for {
        repositories <- deltaClient(request).repositories.getGithub(
          organizationId = Some(org.id),
          existingProject = Some(false),
          limit = Pagination.DefaultLimit+1,
          offset = repositoriesPage * Pagination.DefaultLimit
        )
      } yield {
        Ok(
          views.html.projects.githubOrg(
            uiData(request), org, PaginatedCollection(repositoriesPage, repositories)
          )
        )
      }
    }
  }

  def postGithubOrg(
    orgId: String,
    name: String,
    repositoriesPage: Int = 0
  ) = Identified.async { implicit request =>
    withOrganization(request, orgId) { org =>
      deltaClient(request).repositories.getGithub(
        organizationId = Some(org.id),
        name = Some(name)
      ).flatMap { selected =>
        selected.headOption match {
          case None => Future {
            Redirect(routes.ProjectsController.github()).flashing("warning" -> "Project not found")
          }
          case Some(repo) => {
            deltaClient(request).projects.post(
              ProjectForm(
                organization = org.id,
                name = repo.name,
                scms = Scms.Github,
                visibility = repo.visibility,
                uri = repo.uri
              )
            ).map { project =>
              Redirect(routes.ProjectsController.show(project.id)).flashing("success" -> "Project added")
            }
          }
        }
      }
    }
  }

  def create() = Identified.async { implicit request =>
    organizations(request).map { orgs =>
      Ok(
        views.html.projects.create(
          uiData(request),
          ProjectsController.uiForm,
          orgs
        )
      )
    }
  }

  def postCreate() = Identified.async { implicit request =>
    val boundForm = ProjectsController.uiForm.bindFromRequest

    organizations(request).flatMap { orgs =>
      boundForm.fold (

        formWithErrors => Future {
          Ok(views.html.projects.create(uiData(request), formWithErrors, orgs))
        },

        uiForm => {
          deltaClient(request).projects.post(
            projectForm = ProjectForm(
              organization = uiForm.organization,
              name = uiForm.name,
              scms = Scms(uiForm.scms),
              visibility = Visibility(uiForm.visibility),
              uri = uiForm.uri
            )
          ).map { project =>
            Redirect(routes.ProjectsController.show(project.id)).flashing("success" -> "Project created")
          }.recover {
            case response: io.flow.delta.v0.errors.ErrorsResponse => {
              Ok(views.html.projects.create(uiData(request), boundForm, orgs, response.errors.map(_.message)))
            }
          }
        }
      )
    }
  }

  def edit(id: String) = Identified.async { implicit request =>
    withProject(request, id) { project =>
      organizations(request).map { orgs =>
        Ok(
          views.html.projects.edit(
            uiData(request),
            project,
            ProjectsController.uiForm.fill(
              ProjectsController.UiForm(
                organization = project.organization.id,
                name = project.name,
                scms = project.scms.toString,
                visibility = project.visibility.toString,
                uri = project.uri
              )
            ),
            orgs
          )
        )
      }
    }
  }

  def postEdit(id: String) = Identified.async { implicit request =>
    organizations(request).flatMap { orgs =>
      withProject(request, id) { project =>
        val boundForm = ProjectsController.uiForm.bindFromRequest
          boundForm.fold (

            formWithErrors => Future {
              Ok(views.html.projects.edit(uiData(request), project, formWithErrors, orgs))
            },

            uiForm => {
              deltaClient(request).projects.putById(
                project.id,
                ProjectForm(
                  organization = project.organization.id,
                  name = uiForm.name,
                  scms = Scms(uiForm.scms),
                  visibility = Visibility(uiForm.visibility),
                  uri = uiForm.uri
                )
              ).map { project =>
                Redirect(routes.ProjectsController.show(project.id)).flashing("success" -> "Project updated")
              }.recover {
                case response: io.flow.delta.v0.errors.ErrorsResponse => {
                  Ok(views.html.projects.edit(uiData(request), project, boundForm, orgs, response.errors.map(_.message)))
                }
              }
            }
          )
      }
    }
  }

  def postDelete(id: String) = Identified.async { implicit request =>
    deltaClient(request).projects.deleteById(id).map { response =>
      Redirect(routes.ProjectsController.index()).flashing("success" -> s"Project deleted")
    }.recover {
      case UnitResponse(404) => {
        Redirect(routes.ProjectsController.index()).flashing("warning" -> s"Project not found")
      }
    }
  }

  /**
   * Toggle the setting w/ the given name
   */
  def postSettingsToggle(id: String, name: String) = Identified.async { implicit request =>
    deltaClient(request).projects.getSettingsById(id).flatMap { settings =>
      val form = name match {
        case "syncMasterSha" => {
          Some(SettingsForm(syncMasterSha = Some(!settings.syncMasterSha)))
        }
        case "tagMaster" => {
          Some(SettingsForm(tagMaster = Some(!settings.tagMaster)))
        }
        case "setExpectedState" => {
          Some(SettingsForm(setExpectedState = Some(!settings.setExpectedState)))
        }
        case other => {
          None
        }
      }

      form match {
        case None => {
          Future {
            Redirect(routes.ProjectsController.show(id)).flashing("warning" -> s"Unknown setting")
          }
        }
        case Some(f) => {
          deltaClient(request).projects.putSettingsById(id, f).map { _ =>
            Redirect(routes.ProjectsController.show(id)).flashing("success" -> s"Settings updated")
          }
        }
      }
    }
  }

  def withProject[T](
    request: IdentifiedRequest[T],
    id: String
  )(
    f: Project => Future[Result]
  ) = {
    deltaClient(request).projects.getById(id).flatMap { project =>
      f(project)
    }.recover {
      case UnitResponse(404) => {
        Redirect(routes.ProjectsController.index()).flashing("warning" -> s"Project not found")
      }
    }
  }

}

object ProjectsController {

  case class UiForm(
    organization: String,
    name: String,
    scms: String,
    visibility: String,
    uri: String
  )

  private val uiForm = Form(
    mapping(
      "organization" -> nonEmptyText,
      "name" -> nonEmptyText,
      "scms" -> nonEmptyText,
      "visibility" -> nonEmptyText,
      "uri" -> nonEmptyText
    )(UiForm.apply)(UiForm.unapply)
  )

}
