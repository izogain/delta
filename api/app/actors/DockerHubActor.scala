package io.flow.delta.actors

import db.{ImagesDao, TagsDao}
import io.flow.delta.api.lib.Repo
import io.flow.delta.v0.models._
import io.flow.docker.registry.v0.Client
import io.flow.docker.registry.v0.models.Tag
import io.flow.play.actors.Util
import io.flow.postgresql.{Authorization, OrderBy}
import akka.actor.Actor
import play.api.Logger
import play.api.libs.concurrent.Akka
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import play.api.Play.current
import scala.util.{Failure, Success, Try}

object DockerHubActor {

  lazy val SystemUser = db.UsersDao.systemUser

  trait Message

  object Messages {
    case class Data(projectId: String) extends Message
    case object SyncImages extends Message
    case class Build(version: String) extends Message
  }

}

class DockerHubActor extends Actor with Util with DataProject with EventLog {

 implicit val dockerHubActorExecutionContext: ExecutionContext = Akka.system.dispatchers.lookup("dockerhub-actor-context")

  private[this] val client = new Client
  private[this] val IntervalSeconds = 30

  override val logPrefix = "DockerHubActor"

  def receive = {

    case m @ DockerHubActor.Messages.Data(projectId) => withVerboseErrorHandler(m.toString) {
      setDataProject(projectId)
    }

    case m @ DockerHubActor.Messages.Build(version) => withVerboseErrorHandler(m.toString) {
      withProject { project =>
        withRepo { repo =>
          syncImages(project, repo)

          ImagesDao.findByProjectIdAndVersion(project.id, version) match {
            case Some(image) => {
              log.checkpoint(s"Docker hub image $repo:$version is ready - image id[${image.id}]")
              // Don't fire an event; the ImagesDao will already have
              // raised ImageCreated
            }

            case None => {
              log.checkpoint(s"Docker hub image $repo:$version is not ready. Will check again in $IntervalSeconds seconds")
              Akka.system.scheduler.scheduleOnce(Duration(IntervalSeconds, "seconds")) {
                self ! DockerHubActor.Messages.Build(version)
              }
            }
          }
        }
      }
    }

   case m @ DockerHubActor.Messages.SyncImages => withVerboseErrorHandler(m) {
     withProject { project =>
       withRepo { repo =>
         syncImages(project, repo)

         // Ensure docker images for most recent 5 tags. Eventually
         // should consider if we make images for all tags, or all
         // tags created in last week or ???
         TagsDao.findAll(
           Authorization.All,
           projectId = Some(project.id),
           limit = 5,
           orderBy = OrderBy("-tags.created_at")
         ).foreach { tag =>
           ImagesDao.findByProjectIdAndVersion(project.id, tag.name) match {
             case None => {
               log.checkpoint(s"Docker hub image $repo/${tag.name} not found in local DB. Building")
               sender ! MainActor.Messages.BuildDockerImage(project.id, tag.name)
             }
             case Some(_) => {
               // No-op
             }
           }
         }

       }
     }
   }

   case m: Any => logUnhandledMessage(m)
 }


  def syncImages(project: Project, repo: Repo) {
    println(s"syncImages(${project.id})")
    for {
      tags <- client.tags.get(repo.owner, repo.project)
    } yield {
      println(" - docker hub image tags: " + tags)
      tags.foreach { tag =>
        Try(
          syncImageIfNotExists(project.id, repo, tag.name)
        ) match {
          case Success(_) => // No-op
          case Failure(ex) => {
            println("ERROR syncing docker image: " + ex)
            ex.printStackTrace(System.err)
          }
        }
      }
    }
  }
  
  // This method doesn't actually create the docker image - just syncs
  // an image in the database and thus will execute quickly with no
  // external dependencies. I'm not sure it's worth logging anything
  // here - but we do need to think about how to build the docker
  // image.
  def syncImageIfNotExists(projectId: String, repo: Repo, version: String) {
    ImagesDao.findByProjectIdAndVersion(projectId, version) match {
      case Some(_) => {
        // Image already exists in DB - do nothing
      }

      case None => {
        ImagesDao.findByProjectIdAndVersion(projectId, version) match {
          case Some(img) => {
            // No-op
          }
          case None => {
            // TODO - should never have a failure here
            ImagesDao.create(
              MainActor.SystemUser,
              ImageForm(projectId, repo.toString, version)
            ) match {
              case Left(msgs) => log.completed(s"Failed to create image [${repo.owner}/${repo.project}:$version].")
              case Right(img) => log.completed(s"Synced image [${repo.owner}/${repo.project}:$version] as id ${img.id}")
            }
          }
        }
      }
    }
  }

}
