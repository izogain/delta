package io.flow.delta.actors

import db.{ImagesDao, ImagesWriteDao, UsersDao}
import io.flow.delta.api.lib.Semver
import io.flow.delta.v0.models._
import io.flow.docker.registry.v0.models.{BuildTag, BuildForm}
import io.flow.docker.registry.v0.Client
import io.flow.play.actors.ErrorHandler
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

    /**
      * Message to start the build the docker image for the specified
      * version. Note the current implementation does not actually
      * trigger a build - just watches docker until the build
      * completed - thus assuming an automated build in docker hub.
      */
    case class Build(version: String) extends Message

    case object Setup extends Message
  }

  trait Factory {
    def apply(projectId: String): Actor
  }
  
}

class DockerHubActor @javax.inject.Inject() (
  imagesWriteDao: ImagesWriteDao,
  @com.google.inject.assistedinject.Assisted projectId: String
) extends Actor with ErrorHandler with DataProject with EventLog {

  implicit val dockerHubActorExecutionContext = Akka.system.dispatchers.lookup("dockerhub-actor-context")

  private[this] lazy val v2client = {
    val config = play.api.Play.current.injector.instanceOf[DefaultConfig]
    new Client(
      defaultHeaders = Seq(
        ("Authorization", s"Bearer ${config.requiredString("docker.jwt.token").replaceFirst("JWT ", "")}")
      )
    )
  }

  private[this] val IntervalSeconds = 30

  def receive = {

    case msg @ DockerHubActor.Messages.Setup => withVerboseErrorHandler(msg) {
      setProjectId(projectId)
    }

    case msg @ DockerHubActor.Messages.Build(version) => withVerboseErrorHandler(msg) {
      println(s"DockerHubActor.Messages.Build($version)")
      withProject { project =>
        println(s" project[${project.id}]")
        withOrganization { org =>
          println(s" org[${org.id}]")

          println(s" staritng post auto build")
          v2client.DockerRepositories.postAutobuild(
            org.docker.organization, project.id, createBuildForm(org.docker.organization, project.id)
          ).map { dockerHubBuild =>
            log.completed(s"Docker Hub repository and automated build [${dockerHubBuild.repoWebUrl}] created.")
          }.recover {
            case unitResponse: io.flow.docker.registry.v0.errors.UnitResponse => //don't want to log repository exists every time
            case err => log.message(s"Error creating Docker Hub repository and automated build: $err")
          }

          println(s" staritng sync images")

          syncImages(org.docker, project)

          val imageFullName = s"${org.docker.organization}/${project.id}:$version"

          ImagesDao.findByProjectIdAndVersion(project.id, version) match {
            case Some(image) => {
              log.checkpoint(s"Docker hub image $imageFullName is ready - image id[${image.id}]")
              // Don't fire an event; the ImagesDao will already have
              // raised ImageCreated
            }

            case None => {
              log.checkpoint(s"Docker hub image $imageFullName is not ready. Will check again in $IntervalSeconds seconds")
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


  def syncImages(docker: Docker, project: Project) {
    println(s"syncImages(${docker.organization}, ${project.id})")
    for {
      tags <- v2client.V2Tags.get(docker.organization, project.id)
    } yield {

      tags.results.filter(t => Semver.isSemver(t.name)).foreach { tag =>
        Try(
          upsertImage(docker, project.id, tag.name)
        ) match {
          case Success(_) => // No-op
          case Failure(ex) => {
            ex.printStackTrace(System.err)
          }
        }
      }
    }
  }

  def upsertImage(docker: Docker, projectId: String, version: String) {
    ImagesDao.findByProjectIdAndVersion(projectId, version).getOrElse {
      imagesWriteDao.create(
        UsersDao.systemUser,
        ImageForm(
          projectId = projectId,
          name = s"${docker.organization}/${projectId}",
          version = version
        )
      ) match {
        case Left(msgs) => sys.error(msgs.mkString(", "))
        case Right(img) => // no-op
      }
    }
  }

  def createBuildForm(org: String, name: String): BuildForm = {
    val fullName = s"$org/$name"
    BuildForm(
      active = true,
      buildTags = createBuildTags(),
      description = s"Automated build for $fullName",
      dockerhubRepoName = fullName,
      isPrivate = true,
      name = name,
      namespace = org,
      provider = "github",
      vcsRepoName = fullName
    )
  }

  def createBuildTags(): Seq[BuildTag] = {
    Seq(
      BuildTag(
        dockerfileLocation = "/",
        name = "{sourceref}",
        sourceName = "/^[0-9]+\\.[0-9]+\\.[0-9]+$/",
        sourceType = "Tag"
      )
    )
  }
}
