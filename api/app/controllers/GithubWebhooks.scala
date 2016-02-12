package controllers

import io.flow.delta.actors.MainActor
import io.flow.postgresql.Authorization
import db.ProjectsDao
import play.api.mvc._
import play.api.libs.json._

@javax.inject.Singleton
class GithubWebhooks @javax.inject.Inject() () extends Controller {

  def postByProjectId(projectId: String) = Action { request =>
    ProjectsDao.findById(Authorization.All, projectId) match {
      case None => {
        NotFound
      }
      case Some(project) => {
        play.api.Logger.info(s"Received github webook for project[${project.id}] name[${project.name}]")
        MainActor.ref ! MainActor.Messages.ProjectSync(project.id)
        Ok(Json.toJson(Map("result" -> "success")))
      }
    }
  }

}
