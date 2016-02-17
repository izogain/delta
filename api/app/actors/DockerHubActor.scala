package io.flow.delta.actors

import db.{OrganizationsDao, ImagesDao, UsersDao}
import io.flow.delta.api.lib.Repo
import io.flow.delta.v0.models._
import io.flow.docker.registry.v0.models.{BuildTag, BuildForm}
import io.flow.docker.registry.v0.{Authorization, Client}
import io.flow.play.actors.Util
import io.flow.play.util.DefaultConfig
import akka.actor.Actor
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

    /**
      * Message to start the build the docker image for the specified
      * version. Note the current implementation does not actually
      * trigger a build - just watches docker until the build
      * completed - thus assuming an automated build in docker hub.
      */
    case class Build(version: String) extends Message
  }

}

class DockerHubActor extends Actor with Util with DataProject with EventLog {

 implicit val dockerHubActorExecutionContext: ExecutionContext = Akka.system.dispatchers.lookup("dockerhub-actor-context")

  private[this] lazy val client = new Client(
    auth = Some(
      Authorization.Basic(
        username = DefaultConfig.requiredString("docker.username"),
        password = Some(DefaultConfig.requiredString("docker.password"))
      )
    )
  )

  private[this] lazy val v2client = new Client(
    defaultHeaders = Seq(
      ("Authorization", s"Bearer ${DefaultConfig.requiredString("docker.jwt.token").replaceFirst("JWT ", "")}")
    )
  )

  private[this] val IntervalSeconds = 30

  override val logPrefix = "DockerHubActor"

  def receive = {

    case msg @ DockerHubActor.Messages.Data(projectId) => withVerboseErrorHandler(msg.toString) {
      setDataProject(projectId)
    }

    case msg @ DockerHubActor.Messages.Build(version) => withVerboseErrorHandler(msg.toString) {
      withProject { project =>
        withRepo { repo =>

          OrganizationsDao.findById(io.flow.postgresql.Authorization.All, project.organization.id).map {
            org =>
              val dockerHubOrg = org.docker.organization
              v2client.DockerRepositories.postAutobuild(
                  dockerHubOrg, repo.project, createBuildForm(project, repo, dockerHubOrg)
              ).map { dockerHubBuild =>
                log.completed(s"Docker Hub repository and automated build [${dockerHubBuild.repoWebUrl}] created.")
            }
          }

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

   case msg: Any => logUnhandledMessage(msg)
 }


  def syncImages(project: Project, repo: Repo) {
    println(s"syncImages(${project.id})")
    for {
      tags <- client.tags.get(repo.owner, repo.project)
    } yield {
      println(" - docker hub image tags: " + tags)
      tags.foreach { tag =>
        Try(
          upsertImage(project.id, repo, tag.name)
        ) match {
          case Success(_) => // No-op
          case Failure(ex) => {
            ex.printStackTrace(System.err)
          }
        }
      }
    }
  }

  /**
    * For each tag found in docker hub, creates a local images
    * record. Allows us to query the local database to see if an image
    * exists.
    */
  def upsertImage(projectId: String, repo: Repo, version: String) {
    ImagesDao.findByProjectIdAndVersion(projectId, version).getOrElse {
      ImagesDao.create(
        UsersDao.systemUser,
        ImageForm(projectId, repo.toString, version)
      ) match {
        case Left(msgs) => sys.error(msgs.mkString(", "))
        case Right(img) => // no-op
      }
    }
  }

  def createBuildForm(project: Project, repo: Repo, org: String): BuildForm = {
    BuildForm(
      active = true,
      buildTags = createBuildTags(),
      description = s"Automated build for $org/${repo.project}",
      dockerhubRepoName = s"$org/${repo.project}",
      isPrivate = true,
      name = repo.project,
      namespace = org,
      provider = "github",
      vcsRepoName = s"$org/${repo.project}"
    )
  }

  def createBuildTags(): Seq[BuildTag] = {
    Seq(
      BuildTag(
        dockerfileLocation = "/",
        name = "{sourceref}",
        sourceName = "/^[0-9.]+$$/",
        sourceType = "Tag"
      )
    )
  }
}
