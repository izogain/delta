package controllers

import db.OrganizationsDao
import io.flow.play.clients.UserTokensClient
import io.flow.play.util.Validation
import io.flow.delta.v0.models.{Organization, OrganizationForm}
import io.flow.delta.v0.models.json._
import io.flow.common.v0.models.json._
import play.api.mvc._
import play.api.libs.json._

class Organizations @javax.inject.Inject() (
  val userTokensClient: UserTokensClient
) extends Controller with BaseIdentifiedRestController {

  import scala.concurrent.ExecutionContext.Implicits.global

  def get(
    id: Option[String],
    ids: Option[Seq[String]],
    userId: Option[String],
    limit: Long = 25,
    offset: Long = 0
  ) = Identified { request =>
    Ok(
      Json.toJson(
        OrganizationsDao.findAll(
          authorization(request),
          id = id,
          ids = optionals(ids),
          userId = userId,
          limit = limit,
          offset = offset
        )
      )
    )
  }

  def getById(id: String) = Identified { request =>
    withOrganization(request.user, id) { organization =>
      Ok(Json.toJson(organization))
    }
  }

  def post() = Identified(parse.json) { request =>
    request.body.validate[OrganizationForm] match {
      case e: JsError => {
        UnprocessableEntity(Json.toJson(Validation.invalidJson(e)))
      }
      case s: JsSuccess[OrganizationForm] => {
        OrganizationsDao.create(request.user, s.get) match {
          case Left(errors) => UnprocessableEntity(Json.toJson(Validation.errors(errors)))
          case Right(organization) => Created(Json.toJson(organization))
        }
      }
    }
  }

  def putById(id: String) = Identified(parse.json) { request =>
    withOrganization(request.user, id) { organization =>
      request.body.validate[OrganizationForm] match {
        case e: JsError => {
          UnprocessableEntity(Json.toJson(Validation.invalidJson(e)))
        }
        case s: JsSuccess[OrganizationForm] => {
          OrganizationsDao.update(request.user, organization, s.get) match {
            case Left(errors) => UnprocessableEntity(Json.toJson(Validation.errors(errors)))
            case Right(updated) => Ok(Json.toJson(updated))
          }
        }
      }
    }
  }

  def deleteById(id: String) = Identified { request =>
    withOrganization(request.user, id) { organization =>
      OrganizationsDao.softDelete(request.user, organization)
      NoContent
    }
  }
}
