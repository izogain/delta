package io.flow.delta.actors

import db.{OrganizationsDao, ImagesDao, UsersDao}
import io.flow.delta.api.lib.Repo
import io.flow.delta.v0.models._
import io.flow.docker.registry.v0.{Authorization, Client}
import io.flow.play.actors.Util
import io.flow.play.util.DefaultConfig
import akka.actor.Actor
import play.api.Logger
import play.api.libs.concurrent.Akka
import play.api.libs.json.Json
import scala.concurrent.{Future, ExecutionContext}
import scala.concurrent.duration._
import play.api.Play.current
import scala.util.{Failure, Success, Try}
import play.api.libs.ws._

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

  private[this] val IntervalSeconds = 30

  override val logPrefix = "DockerHubActor"

  def receive = {

    case msg @ DockerHubActor.Messages.Data(projectId) => withVerboseErrorHandler(msg.toString) {
      setDataProject(projectId)
    }

    case msg @ DockerHubActor.Messages.Build(version) => withVerboseErrorHandler(msg.toString) {
      withProject { project =>
        withRepo { repo =>
          val org = OrganizationsDao.findById(io.flow.postgresql.Authorization.All, project.organization.id).get

          for {
            checkDockerHubRepo <- checkDockerHubRepository(project, repo, org.docker.organization)
            if checkDockerHubRepo.status != 200
            createDockerHubRepo <- createDockerHubRepository(project, repo, org.docker.organization)
          } yield {
            println(s"DUUUUUDE(${createDockerHubRepo.body})")
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

  def checkDockerHubRepository(project: Project, repo: Repo, org: String): Future[WSResponse] = {
    // TODO: add routes to an API
    // TODO: since Docker Hub API documentation is scarce, ensure other response codes are handled

    // rudimentary Docker Hub V2 API calls to
    // Check if docker hub repository exists
    WS.url(s"https://hub.docker.com/v2/repositories/${org}/${repo.project}/").withHeaders(("Authorization", DefaultConfig.requiredString("docker.jwt.token"))).get().map {
      response =>
        response.status match {
          case 400 =>
            Logger.info(s"Docker Hub repository [${org}/${repo.project}] not found, will try to create.")
          case 200 =>
            Logger.info(s"Docker Hub repository [${org}/${repo.project}] already exists, nothing to create.")
          case _ => //no-op?
        }
        response
    }
  }

  def createDockerHubRepository(project: Project, repo: Repo, org: String): Future[WSResponse] = {
    val payload = Json.parse(
      s"""
         |{
         |    "name":"${repo.project}",
         |    "namespace":"${org}",
         |    "description":"Automated build for ${org}",
         |    "vcs_repo_name":"${org}/${repo.project}",
         |    "provider":"github",
         |    "dockerhub_repo_name":"${org}/${repo.project}",
         |    "is_private":true,
         |    "active":true,
         |    "build_tags":
         |    [
         |        {
         |            "name":"{sourceref}","source_type":"Tag","source_name":"/^[0-9.]+$$/","dockerfile_location":"/"
         |        },
         |        {
         |            "name":"{sourceref}","source_type":"Branch","source_name":"/^([^m]|.[^a]|..[^s]|...[^t]|....[^e]|.....[^r]|.{0,5}$$|.{7,})/","dockerfile_location":"/"
         |        }
         |    ]
         |}
         |
                      """.stripMargin)
    WS.url(s"https://hub.docker.com/v2/repositories/${org}/${repo.project}/autobuild/").withHeaders(("Authorization", DefaultConfig.requiredString("docker.jwt.token"))).post(payload).map {
      response =>
        response.status match {
          case 401 => Logger.warn(s"Unable to create Docker Hub repository [${org}/${repo.project}].")
          case 201 => Logger.info(s"Docker Hub repository [${org}/${repo.project}] created.")
          case _ => //no-op?
        }
        response
    }
  }
}
