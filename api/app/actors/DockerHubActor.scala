package io.flow.delta.actors

import db.ImagesDao
import io.flow.delta.api.lib.Repo
import io.flow.delta.v0.models._
import io.flow.docker.registry.v0.Client
import io.flow.docker.registry.v0.models.Tag
import io.flow.play.actors.Util
import akka.actor.Actor
import play.api.Logger
import play.api.libs.concurrent.Akka
import scala.concurrent.ExecutionContext
import play.api.Play.current
import scala.util.{Failure, Success, Try}

object DockerHubActor {

  lazy val SystemUser = db.UsersDao.systemUser

  trait Message

  object Messages {
    case class Data(projectId: String) extends Message
    case object SyncImages extends Message
  }

}

class DockerHubActor extends Actor with Util with DataProject with EventLog {

 implicit val dockerHubActorExecutionContext: ExecutionContext = Akka.system.dispatchers.lookup("dockerhub-actor-context")

  private[this] val client = new Client

  override val logPrefix = "DockerHubActor"

  def receive = {

    case m @ DockerHubActor.Messages.Data(projectId) => withVerboseErrorHandler(m.toString) {
      setDataProject(projectId)
    }

   case m @ DockerHubActor.Messages.SyncImages => withVerboseErrorHandler(m) {
     withProject { project =>
       withRepo { repo =>
         for {
           tags <- client.tags.get(repo.owner, repo.project)
         } yield {
           tags.foreach { tag =>
             Try(createImage(project.id, repo, tag)) match {
               case Success(_) => // No-op
               case Failure(ex) => {
                 ex.printStackTrace(System.err)
                 println("ERROR syncing docker image: " + ex)
               }
             }
           }
         }
       }
     }
   }

   case m: Any => logUnhandledMessage(m)
 }


 def createImageForm(projectId: String, repo: Repo, tag: Tag): ImageForm = {
   ImageForm(
     projectId,
     repo.toString,
     tag.name
   )
 }

  // This method doesn't actually create the docker image - just syncs
  // an image in the database and thus will execute quickly with no
  // external dependencies. I'm not sure it's worth logging anything
  // here - but we do need to think about how to build the docker
  // image.
  def createImage(projectId: String, repo: Repo, tag: Tag) = {
    log.started(s"Creating image [${repo.owner}/${repo.project}:${tag.name}] if it does not already exist.")
    val checkImageExists = ImagesDao.findByNameAndVersion(repo.project, tag.name)
    checkImageExists match {
      case Some(img) => log.completed(s"Image [${repo.owner}/${repo.project}:${tag.name}] already exists, no image created.")
      case None => {
        val imageCreate = ImagesDao.create(MainActor.SystemUser, createImageForm(projectId, repo, tag))
        imageCreate match {
          case Left(msgs) => log.completed(s"Failed to create image [${repo.owner}/${repo.project}:${tag.name}].")
          case Right(img) => log.completed(s"Image [${repo.owner}/${repo.project}:${tag.name}] created.")
        }
      }
    }
  }
}
