package io.flow.delta.actors


import db.{ProjectsDao, ImagesDao}
import io.flow.delta.api.lib.{GithubUtil, EventLog, Repo}
import io.flow.delta.v0.models._
import io.flow.docker.registry.v0.Client
import io.flow.docker.registry.v0.models.Tag
import io.flow.play.actors.Util
import akka.actor.Actor
import io.flow.postgresql.Authorization
import play.api.Logger
import play.api.libs.concurrent.Akka
import scala.concurrent.ExecutionContext
import play.api.Play.current

object DockerHubActor {

  lazy val SystemUser = db.UsersDao.systemUser

  trait Message

  object Messages {
    case class Data(projectId: String) extends Message
    case object SyncImages extends Message
  }

}

class DockerHubActor extends Actor with Util {

 implicit val dockerHubActorExecutionContext: ExecutionContext = Akka.system.dispatchers.lookup("dockerhub-actor-context")

  private[this] var dataProject: Option[Project] = None
  private[this] var dataRepo: Option[Repo] = None
  private[this] var projectId: String = _
  private[this] val client = new Client

  private[this] def log: EventLog = {
    dataProject.map {
      EventLog.withSystemUser(_, "DockerHubActor")
    }.getOrElse {
      sys.error("Cannot get log with empty data")
    }
  }

  def receive = {

    case m @ DockerHubActor.Messages.Data(projectId) => withVerboseErrorHandler(m.toString) {
      this.projectId = projectId
      ProjectsDao.findById(Authorization.All, projectId) match {
        case None => {
          dataProject = None
          dataRepo = None
        }
        case Some(project) => {
          dataProject = Some(project)
          dataRepo = GithubUtil.parseUri(project.uri) match {
            case Left(error) => {
              Logger.warn(s"Project id[${project.id}] name[${project.name}]: $error")
              None
            }
            case Right(repo) => {
              Some(repo)
            }
          }
        }
      }
    }

   case m @ DockerHubActor.Messages.SyncImages => withVerboseErrorHandler(m) {
     dataRepo.foreach { repo =>
       for {
         tags <- client.tags.get(repo.owner, repo.project)
       } {
         tags.map(tag => ImagesDao.create(MainActor.SystemUser, createImageForm(repo, tag)))
       }
     }
   }

   case m: Any => logUnhandledMessage(m)
 }


 def createImageForm(repo: Repo, tag: Tag): ImageForm = {
   ImageForm(
     projectId,
     repo.project,
     tag.name
   )
 }
}
