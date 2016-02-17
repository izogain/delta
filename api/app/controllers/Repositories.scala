package controllers

import db.{OrganizationsDao, ProjectsDao}
import io.flow.common.v0.models.json._
import io.flow.delta.api.lib.Github
import io.flow.delta.v0.models.json._
import io.flow.github.v0.models.Repository
import io.flow.github.v0.models.json._
import io.flow.play.clients.UserTokensClient
import io.flow.play.util.Validation
import play.api.mvc._
import play.api.libs.json._
import scala.concurrent.Future

class Repositories @javax.inject.Inject() (
  val userTokensClient: UserTokensClient,
  val github: Github
) extends Controller with BaseIdentifiedRestController {

  import scala.concurrent.ExecutionContext.Implicits.global

  def getGithub(
    name: Option[String] = None,
    organizationId: Option[String] = None,
    existingProject: Option[Boolean] = None,
    limit: Long = 5,
    offset: Long = 0
  ) = Identified { request =>
    if (!existingProject.isEmpty && organizationId.isEmpty) {
      UnprocessableEntity(Json.toJson(Validation.error("When filtering by existing projects, you must also provide the organization_id")))

    } else {
      val auth = authorization(request)
      val org = organizationId.flatMap { OrganizationsDao.findById(auth, _)}

      val results = github.repositories(request.user, offset, limit) { r =>
        (name match {
          case None => true
          case Some(n) => n == r.name
        }) &&
        (org match {
          case None => true
          case Some(org) => {
            existingProject.isEmpty ||
            existingProject == Some(true) && !ProjectsDao.findByOrganizationIdAndName(auth, org.id, r.name).isEmpty ||
            existingProject == Some(false) && ProjectsDao.findByOrganizationIdAndName(auth, org.id, r.name).isEmpty
          }
        })
      }

      Ok(Json.toJson(results))
    }
  }

}
