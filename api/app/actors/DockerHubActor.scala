package io.flow.delta.actors

import db.{ImagesDao, ImagesWriteDao, UsersDao}
import io.flow.delta.api.lib.{BuildNames, Semver}
import io.flow.delta.v0.models._
import io.flow.docker.registry.v0.models.{BuildTag => DockerBuildTag, BuildForm => DockerBuildForm}
import io.flow.docker.registry.v0.Client
import io.flow.play.actors.ErrorHandler
import io.flow.play.util.DefaultConfig
import akka.actor.Actor
import org.joda.time.DateTime
import play.api.libs.concurrent.Akka
import play.api.Logger
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

    case class Monitor(version: String, start: DateTime) extends Message    

    case object Setup extends Message
  }

  trait Factory {
    def apply(buildId: String): Actor
  }
  
}

class DockerHubActor @javax.inject.Inject() (
  imagesWriteDao: ImagesWriteDao,
  @com.google.inject.assistedinject.Assisted buildId: String
) extends Actor with ErrorHandler with DataBuild with EventLog {

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
  private[this] val TimeoutSeconds = 1500

  def receive = {

    case msg @ DockerHubActor.Messages.Setup => withVerboseErrorHandler(msg) {
      setBuildId(buildId)
    }

    case msg @ DockerHubActor.Messages.Build(version) => withVerboseErrorHandler(msg) {
      withOrganization { org =>
        withProject { project =>
          withBuild { build =>
            v2client.DockerRepositories.postAutobuild(
              org.docker.organization, BuildNames.projectName(build), createBuildForm(org.docker, project.scms, project.uri, build)
            ).map { dockerHubBuild =>
              // TODO: Log the docker hub URL and not the VCS url
              log.completed(s"Docker Hub repository and automated build [${dockerHubBuild.repoWebUrl}] created.")
            }.recover {
              case io.flow.docker.registry.v0.errors.UnitResponse(code) => {
                code match {
                  case 400 => // automated build already exists
                  case _ => {
                    log.completed(s"Docker Hub returned HTTP $code when trying to create automated build")
                  }
                }
              }
              case err => {
                err.printStackTrace(System.err)
                log.completed(s"Error creating Docker Hub repository and automated build: $err", Some(err))
              }
            }

            self ! DockerHubActor.Messages.Monitor(version, new DateTime())
          }
        }
      }
    }

    case msg @ DockerHubActor.Messages.Monitor(version, start) => withVerboseErrorHandler(msg) {
      withBuild { build =>
        withOrganization { org =>
          syncImages(org.docker, build)

          val imageFullName = BuildNames.dockerImageName(org.docker, build, version)

          ImagesDao.findByBuildIdAndVersion(build.id, version) match {
            case Some(image) => {
              log.completed(s"Docker hub image $imageFullName is ready - id[${image.id}]")
              // Don't fire an event; the ImagesDao will already have
              // raised ImageCreated
            }

            case None => {
              if (start.plusSeconds(TimeoutSeconds).isBefore(new DateTime)) {
                val ex = new java.util.concurrent.TimeoutException()
                log.completed(s"Timeout after $TimeoutSeconds seconds. Docker image $imageFullName was not built", Some(ex))

              } else {
                log.checkpoint(s"Docker hub image $imageFullName is not ready. Will check again in $IntervalSeconds seconds")
                Akka.system.scheduler.scheduleOnce(Duration(IntervalSeconds, "seconds")) {
                  self ! DockerHubActor.Messages.Monitor(version, start)
                }
              }
            }
          }
        }
      }
    }

    case msg: Any => logUnhandledMessage(msg)
 }


  def syncImages(docker: Docker, build: Build) {
    for {
      tags <- v2client.V2Tags.get(docker.organization, BuildNames.projectName(build))
    } yield {

      tags.results.filter(t => Semver.isSemver(t.name)).foreach { tag =>
        Try(
          upsertImage(docker, build, tag.name)
        ) match {
          case Success(_) => // No-op
          case Failure(ex) => {
            ex.printStackTrace(System.err)
          }
        }
      }
    }
  }

  def upsertImage(docker: Docker, build: Build, version: String) {
    ImagesDao.findByBuildIdAndVersion(buildId, version).getOrElse {
      imagesWriteDao.create(
        UsersDao.systemUser,
        ImageForm(
          buildId = buildId,
          name = BuildNames.dockerImageName(docker, build),
          version = version
        )
      ) match {
        case Left(msgs) => sys.error(msgs.mkString(", "))
        case Right(img) => // no-op
      }
    }
  }

  def createBuildForm(docker: Docker, scms: Scms, scmsUri: String, build: Build): DockerBuildForm = {
    val fullName = BuildNames.dockerImageName(docker, build)
    val buildTags = createBuildTags(build.dockerfilePath)

    val vcsRepoName = io.flow.delta.api.lib.GithubUtil.parseUri(scmsUri) match {
      case Left(errors) => {
        Logger.warn(s"Error parsing VCS URI[$scmsUri]. defaulting vcsRepoName to[$fullName]: ${errors.mkString(", ")}")
        fullName
      }
      case Right(repo) => {
        repo.toString
      }
    }

    DockerBuildForm(
      active = true,
      buildTags = buildTags,
      description = s"Automated build for $fullName",
      dockerhubRepoName = fullName,
      isPrivate = true,
      name = BuildNames.projectName(build),
      namespace = docker.organization,
      provider = scms match {
        case Scms.Github => "github"
        case Scms.UNDEFINED(other) => other
      },
      vcsRepoName = vcsRepoName
    )
  }

  def createBuildTags(dockerfilePath: String): Seq[DockerBuildTag] = {
    Seq(
      DockerBuildTag(
        dockerfileLocation = dockerfilePath.replace("Dockerfile", ""),
        name = "{sourceref}",
        sourceName = "/^[0-9]+\\.[0-9]+\\.[0-9]+$/",
        sourceType = "Tag"
      )
    )
  }
}
