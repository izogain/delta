package controllers

import db.{Authorization, OrganizationsDao, ProjectsDao}
import io.flow.delta.api.lib.Github
import io.flow.delta.v0.models.json._
import io.flow.common.v0.models.json._
import io.flow.play.clients.UserTokensClient
import io.flow.play.controllers.IdentifiedRestController
import io.flow.play.util.Validation
import play.api.mvc._
import play.api.libs.json._
import scala.concurrent.Future

class Repositories @javax.inject.Inject() (
  val userTokensClient: UserTokensClient,
  val github: Github
) extends Controller with IdentifiedRestController {

  import scala.concurrent.ExecutionContext.Implicits.global

  def getGithub(
    name: Option[String] = None,
    organizationId: Option[String] = None,
    existingProject: Option[Boolean] = None,
    limit: Long = 25,
    offset: Long = 0
  ) = Identified.async { request =>
    if (!existingProject.isEmpty && organizationId.isEmpty) {
      Future {
        UnprocessableEntity(Json.toJson(Validation.error("When filtering by existing projects, you must also provide the organization_id")))
      }
    } else {
      val auth = Authorization.User(request.user.id)
      github.repositories(request.user).map { repos =>
        Ok(
          Json.toJson(
            repos.
              filter { r => name.isEmpty || name == Some(r.name) }.
              filter { r =>
                organizationId.flatMap { OrganizationsDao.findById(auth, _) } match {
                  case None => true
                  case Some(org) => {
                    existingProject.isEmpty ||
                    existingProject == Some(true) && !ProjectsDao.findByOrganizationAndName(auth, org.key, r.name).isEmpty ||
                    existingProject == Some(false) && ProjectsDao.findByOrganizationAndName(auth, org.key, r.name).isEmpty
                  }
                }
              }.
              drop(offset.toInt).
              take(limit.toInt)
          )
        )
      }
    }
  }

}
