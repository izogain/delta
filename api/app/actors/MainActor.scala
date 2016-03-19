package io.flow.delta.actors

import db.ProjectsDao
import io.flow.delta.api.lib.StateDiff
import io.flow.play.actors.{ErrorHandler, Scheduler}
import io.flow.postgresql.{Authorization, Pager}
import play.api.libs.concurrent.Akka
import akka.actor._
import play.api.{Application, Logger}
import play.api.Play.current
import play.api.libs.concurrent.InjectedActorSupport
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

object MainActor {

  lazy val SystemUser = db.UsersDao.systemUser

  object Messages {

    case class BuildDockerImage(buildId: String, version: String)
    case class CheckLastState(buildId: String)

    case class ProjectCreated(id: String)
    case class ProjectUpdated(id: String)
    case class ProjectDeleted(id: String)
    case class ProjectSync(id: String)

    case class BuildCreated(id: String)
    case class BuildUpdated(id: String)
    case class BuildDeleted(id: String)
    case class BuildSync(id: String)
    case class BuildCheckTag(id: String, name: String)

    case class BuildDesiredStateUpdated(buildId: String)
    case class BuildLastStateUpdated(buildId: String)

    case class Scale(buildId: String, diffs: Seq[StateDiff])

    case class ShaCreated(projectId: String, id: String)
    case class ShaUpdated(projectId: String, id: String)

    case class TagCreated(projectId: String, id: String, name: String)
    case class TagUpdated(projectId: String, id: String, name: String)

    case class UserCreated(id: String)

    case class ImageCreated(buildId: String, id: String, version: String)

  }
}

@javax.inject.Singleton
class MainActor @javax.inject.Inject() (
  buildFactory: BuildActor.Factory,
  dockerHubFactory: DockerHubActor.Factory,
  projectFactory: ProjectActor.Factory,
  override val config: io.flow.play.util.Config,
  system: ActorSystem
) extends Actor with ActorLogging with ErrorHandler with Scheduler with InjectedActorSupport{

  private[this] implicit val mainActorExecutionContext: ExecutionContext = system.dispatchers.lookup("main-actor-context")

  private[this] val name = "main"

  private[this] val searchActor = system.actorOf(Props[SearchActor], name = s"$name:SearchActor")

  private[this] val buildActors = scala.collection.mutable.Map[String, ActorRef]()
  private[this] val buildSupervisorActors = scala.collection.mutable.Map[String, ActorRef]()
  private[this] val dockerHubActors = scala.collection.mutable.Map[String, ActorRef]()
  private[this] val projectActors = scala.collection.mutable.Map[String, ActorRef]()
  private[this] val projectSupervisorActors = scala.collection.mutable.Map[String, ActorRef]()
  private[this] val userActors = scala.collection.mutable.Map[String, ActorRef]()

  system.scheduler.scheduleOnce(Duration(10, "seconds")) {
    Pager.create { offset =>
      ProjectsDao.findAll(Authorization.All, offset = offset)
    }.foreach { project =>
      self ! MainActor.Messages.ProjectSync(project.id)
    }
  }

  def receive = akka.event.LoggingReceive {

    case msg @ MainActor.Messages.BuildCreated(id) => withVerboseErrorHandler(msg) {
      upsertBuildSupervisorActor(id) ! BuildSupervisorActor.Messages.PursueDesiredState
    }

    case msg @ MainActor.Messages.BuildUpdated(id) => withVerboseErrorHandler(msg) {
      upsertBuildSupervisorActor(id) ! BuildSupervisorActor.Messages.PursueDesiredState
    }

    case msg @ MainActor.Messages.BuildDeleted(id) => withVerboseErrorHandler(msg) {
      (buildActors -= id).map { actor =>
        // TODO: Terminate actor
      }
    }

    case msg @ MainActor.Messages.BuildSync(id) => withVerboseErrorHandler(msg) {
      upsertBuildSupervisorActor(id) ! BuildSupervisorActor.Messages.PursueDesiredState
    }

    case msg @ MainActor.Messages.BuildCheckTag(id, name) => withVerboseErrorHandler(msg) {
      upsertBuildSupervisorActor(id) ! BuildSupervisorActor.Messages.CheckTag(name)
    }

    case msg @ MainActor.Messages.UserCreated(id) => withVerboseErrorHandler(msg) {
      upsertUserActor(id) ! UserActor.Messages.Created
    }

    case msg @ MainActor.Messages.ProjectCreated(id) => withVerboseErrorHandler(msg) {
      self ! MainActor.Messages.ProjectSync(id)
    }

    case msg @ MainActor.Messages.ProjectUpdated(id) => withVerboseErrorHandler(msg) {
      searchActor ! SearchActor.Messages.SyncProject(id)
      upsertProjectSupervisorActor(id) ! ProjectSupervisorActor.Messages.PursueDesiredState
    }

    case msg @ MainActor.Messages.ProjectDeleted(id) => withVerboseErrorHandler(msg) {
      searchActor ! SearchActor.Messages.SyncProject(id)
    }

    case msg @ MainActor.Messages.ProjectSync(id) => withVerboseErrorHandler(msg) {
      upsertProjectActor(id) ! ProjectActor.Messages.SyncBuilds
      upsertProjectSupervisorActor(id) ! ProjectSupervisorActor.Messages.PursueDesiredState
      searchActor ! SearchActor.Messages.SyncProject(id)
    }

    case msg @ MainActor.Messages.Scale(buildId, diffs) => withVerboseErrorHandler(msg) {
      upsertBuildActor(buildId) ! BuildActor.Messages.Scale(diffs)
    }

    case msg @ MainActor.Messages.ShaCreated(projectId, id) => withVerboseErrorHandler(msg) {
      upsertProjectSupervisorActor(projectId) ! ProjectSupervisorActor.Messages.PursueDesiredState
    }

    case msg @ MainActor.Messages.ShaUpdated(projectId, id) => withVerboseErrorHandler(msg) {
      upsertProjectSupervisorActor(projectId) ! ProjectSupervisorActor.Messages.PursueDesiredState
    }

    case msg @ MainActor.Messages.TagCreated(projectId, id, name) => withVerboseErrorHandler(msg) {
      upsertProjectSupervisorActor(projectId) ! ProjectSupervisorActor.Messages.CheckTag(name)
    }

    case msg @ MainActor.Messages.TagUpdated(projectId, id, name) => withVerboseErrorHandler(msg) {
      upsertProjectSupervisorActor(projectId) ! ProjectSupervisorActor.Messages.CheckTag(name)
    }

    case msg @ MainActor.Messages.ImageCreated(buildId, id, version) => withVerboseErrorHandler(msg) {
      upsertBuildSupervisorActor(buildId) ! BuildSupervisorActor.Messages.CheckTag(version)
    }

    case msg @ MainActor.Messages.BuildDockerImage(buildId, version) => withVerboseErrorHandler(msg) {
      upsertDockerHubActor(buildId) ! DockerHubActor.Messages.Build(version)
    }

    case msg @ MainActor.Messages.CheckLastState(buildId) => withVerboseErrorHandler(msg) {
      upsertBuildActor(buildId) ! BuildActor.Messages.CheckLastState
    }

    case msg @ MainActor.Messages.BuildDesiredStateUpdated(buildId) => withVerboseErrorHandler(msg) {
      upsertBuildSupervisorActor(buildId) ! BuildSupervisorActor.Messages.PursueDesiredState
    }

    case msg @ MainActor.Messages.BuildLastStateUpdated(buildId) => withVerboseErrorHandler(msg) {
      upsertBuildSupervisorActor(buildId) ! BuildSupervisorActor.Messages.PursueDesiredState
    }

    case msg: Any => logUnhandledMessage(msg)

  }

  def upsertDockerHubActor(buildId: String): ActorRef = {
    dockerHubActors.lift(buildId).getOrElse {
      val ref = injectedChild(dockerHubFactory(buildId), name = s"$name:dockerHubActor:$buildId")
      ref ! DockerHubActor.Messages.Setup
      dockerHubActors += (buildId -> ref)
      ref
    }
  }

  def upsertUserActor(id: String): ActorRef = {
    userActors.lift(id).getOrElse {
      val ref = system.actorOf(Props[UserActor], name = s"$name:userActor:$id")
      ref ! UserActor.Messages.Data(id)
      userActors += (id -> ref)
      ref
    }
  }

  def upsertProjectActor(id: String): ActorRef = {
    projectActors.lift(id).getOrElse {
      val ref = injectedChild(projectFactory(id), name = s"$name:projectActor:$id")
      ref ! ProjectActor.Messages.Setup
      projectActors += (id -> ref)
      ref
    }
  }

  def upsertBuildActor(id: String): ActorRef = {
    buildActors.lift(id).getOrElse {
      val ref = injectedChild(buildFactory(id), name = s"$name:buildActor:$id")
      ref ! BuildActor.Messages.Setup

      scheduleRecurring(system, "aws.ecs.update.container.seconds") {
        ref !  BuildActor.Messages.UpdateContainerAgent
      }

      buildActors += (id -> ref)
      ref
    }
  }

  def upsertProjectSupervisorActor(id: String): ActorRef = {
    projectSupervisorActors.lift(id).getOrElse {
      val ref = system.actorOf(Props[ProjectSupervisorActor], name = s"$name:projectSupervisorActor:$id")
      ref ! ProjectSupervisorActor.Messages.Data(id)
      projectSupervisorActors += (id -> ref)
      ref
    }
  }

  def upsertBuildSupervisorActor(id: String): ActorRef = {
    buildSupervisorActors.lift(id).getOrElse {
      val ref = system.actorOf(Props[BuildSupervisorActor], name = s"$name:buildSupervisorActor:$id")
      ref ! BuildSupervisorActor.Messages.Data(id)
      buildSupervisorActors += (id -> ref)
      ref
    }
  }
}
