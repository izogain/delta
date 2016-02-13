package controllers

import io.flow.delta.v0.errors.UnitResponse
import io.flow.delta.v0.models.{Organization, OrganizationForm}
import io.flow.delta.www.lib.DeltaClientProvider
import io.flow.play.clients.UserTokensClient
import io.flow.play.util.{Pagination, PaginatedCollection}
import scala.concurrent.Future

import play.api._
import play.api.i18n.MessagesApi
import play.api.mvc._
import play.api.data._
import play.api.data.Forms._

class OrganizationsController @javax.inject.Inject() (
  val messagesApi: MessagesApi,
  override val userTokensClient: UserTokensClient,
  override val deltaClientProvider: DeltaClientProvider
) extends BaseController(userTokensClient, deltaClientProvider) {

  import scala.concurrent.ExecutionContext.Implicits.global
 
  override def section = None

  def redirectToDashboard(org: String) = Identified { implicit request =>
    Redirect(routes.ApplicationController.index(organization = Some(org)))
  }

  def index(page: Int = 0) = Identified.async { implicit request =>
    for {
      organizations <- deltaClient(request).organizations.get(
        limit = Pagination.DefaultLimit+1,
        offset = page * Pagination.DefaultLimit
      )
    } yield {
      Ok(
        views.html.organizations.index(
          uiData(request),
          PaginatedCollection(page, organizations)
        )
      )
    }
  }

  def show(id: String, projectsPage: Int = 0) = Identified.async { implicit request =>
    withOrganization(request, id) { org =>
      for {
        projects <- deltaClient(request).projects.get(
          organization = Some(id),
          limit = Pagination.DefaultLimit+1,
          offset = projectsPage * Pagination.DefaultLimit
        )
      } yield {
        Ok(
          views.html.organizations.show(
            uiData(request),
            org,
            PaginatedCollection(projectsPage, projects)
          )
        )
      }
    }
  }

  def create(returnUrl: Option[String]) = Identified { implicit request =>
    Ok(
      views.html.organizations.create(
        uiData(request),
        OrganizationsController.uiForm.fill(
          OrganizationsController.UiForm(
            id = "",
            returnUrl = returnUrl
          )
        )
      )
    )
  }

  def postCreate() = Identified.async { implicit request =>
    val boundForm = OrganizationsController.uiForm.bindFromRequest
    boundForm.fold (

      formWithErrors => Future {
        Ok(views.html.organizations.create(uiData(request), formWithErrors))
      },

      uiForm => {
        deltaClient(request).organizations.post(uiForm.organizationForm).map { organization =>
          val url = uiForm.returnUrl match {
            case None => {
              routes.OrganizationsController.show(organization.id).path
            }
            case Some(u) => {
              assert(u.startsWith("/"), s"Redirect URL[$u] must start with /")
              u
            }
          }
          Redirect(url).flashing("success" -> "Organization created")
        }.recover {
          case response: io.flow.delta.v0.errors.ErrorsResponse => {
            Ok(views.html.organizations.create(uiData(request), boundForm, response.errors.map(_.message)))
          }
        }
      }
    )
  }

  def edit(id: String) = Identified.async { implicit request =>
    withOrganization(request, id) { organization =>
      Future {
        Ok(
          views.html.organizations.edit(
            uiData(request),
            organization,
            OrganizationsController.uiForm.fill(
              OrganizationsController.UiForm(
                id = organization.id,
                returnUrl = None
              )
            )
          )
        )
      }
    }
  }

  def postEdit(id: String) = Identified.async { implicit request =>
    withOrganization(request, id) { organization =>
      val boundForm = OrganizationsController.uiForm.bindFromRequest
      boundForm.fold (

        formWithErrors => Future {
          Ok(views.html.organizations.edit(uiData(request), organization, formWithErrors))
        },

        uiForm => {
          deltaClient(request).organizations.putById(organization.id, uiForm.organizationForm).map { updated =>
            Redirect(routes.OrganizationsController.show(updated.id)).flashing("success" -> "Organization updated")
          }.recover {
            case response: io.flow.delta.v0.errors.ErrorsResponse => {
              Ok(views.html.organizations.edit(uiData(request), organization, boundForm, response.errors.map(_.message)))
            }
          }
        }
      )
    }
  }

  def postDelete(id: String) = Identified.async { implicit request =>
    withOrganization(request, id) { org =>
      deltaClient(request).organizations.deleteById(org.id).map { response =>
        Redirect(routes.OrganizationsController.index()).flashing("success" -> s"Organization deleted")
      }.recover {
        case UnitResponse(404) => {
          Redirect(routes.OrganizationsController.index()).flashing("warning" -> s"Organization not found")
        }
      }
    }
  }

}

object OrganizationsController {

  case class UiForm(
    id: String,
    returnUrl: Option[String]
  ) {

    val organizationForm = OrganizationForm(
      id = id
    )

  }

  private val uiForm = Form(
    mapping(
      "id" -> nonEmptyText,
      "return_url" -> optional(text)
    )(UiForm.apply)(UiForm.unapply)
  )

}
