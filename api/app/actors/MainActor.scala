package io.flow.delta.actors

import io.flow.delta.api.lib.StateDiff
import io.flow.play.actors.Util
import io.flow.postgresql.Authorization
import play.api.libs.concurrent.Akka
import akka.actor._
import play.api.Logger
import play.api.Play.current
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

object MainActor {

  def props() = Props(new MainActor("main"))

  lazy val ref = Akka.system.actorOf(props(), "main")

  lazy val SystemUser = db.UsersDao.systemUser

  object Messages {

    case class BuildDockerImage(projectId: String, version: String)
    case class CheckLastState(projectId: String)

    case class ProjectCreated(id: String)
    case class ProjectUpdated(id: String)
    case class ProjectDeleted(id: String)
    case class ProjectSync(id: String)

    case class ProjectDesiredStateUpdated(projectId: String)
    case class ProjectLastStateUpdated(projectId: String)

    case class Scale(projectId: String, diffs: Seq[StateDiff])

    case class ShaCreated(projectId: String, id: String)
    case class ShaUpdated(projectId: String, id: String)

    case class TagCreated(projectId: String, id: String, name: String)
    case class TagUpdated(projectId: String, id: String, name: String)

    case class UserCreated(id: String)

    case class ImageCreated(projectId: String, id: String, version: String)

  }
}


class MainActor(name: String) extends Actor with ActorLogging with Util {
  import scala.concurrent.duration._

  private[this] val searchActor = Akka.system.actorOf(Props[SearchActor], name = s"$name:SearchActor")

  private[this] val dockerHubActors = scala.collection.mutable.Map[String, ActorRef]()
  private[this] val projectActors = scala.collection.mutable.Map[String, ActorRef]()
  private[this] val supervisorActors = scala.collection.mutable.Map[String, ActorRef]()
  private[this] val userActors = scala.collection.mutable.Map[String, ActorRef]()

  private[this] val periodicActor = Akka.system.actorOf(Props[PeriodicActor], name = s"$name:periodicActor")
  scheduleRecurring(periodicActor, "io.flow.delta.api.CheckProjects.seconds", PeriodicActor.Messages.CheckProjects)

  implicit val mainActorExecutionContext: ExecutionContext = Akka.system.dispatchers.lookup("main-actor-context")

  Akka.system.scheduler.scheduleOnce(Duration(1, "seconds")) {
    periodicActor ! PeriodicActor.Messages.Startup
  }

  def receive = akka.event.LoggingReceive {

    case msg @ MainActor.Messages.UserCreated(id) => withVerboseErrorHandler(msg) {
      upsertUserActor(id) ! UserActor.Messages.Created
    }

    case msg @ MainActor.Messages.ProjectCreated(id) => withVerboseErrorHandler(msg) {
      val actor = upsertProjectActor(id)

      // TODO: should we do this inside Project Actor every time it
      // received the data object? Would allow us to make sure things
      // are setup every time the actor starts (vs. just on project
      // creation)
      actor ! ProjectActor.Messages.CreateHooks
      actor ! ProjectActor.Messages.ConfigureECS // One-time ECS setup
      actor ! ProjectActor.Messages.ConfigureEC2 // One-time EC2 setup
      upsertSupervisorActor(id) ! SupervisorActor.Messages.PursueDesiredState
      searchActor ! SearchActor.Messages.SyncProject(id)
    }

    case msg @ MainActor.Messages.ProjectUpdated(id) => withVerboseErrorHandler(msg) {
      searchActor ! SearchActor.Messages.SyncProject(id)
      upsertSupervisorActor(id) ! SupervisorActor.Messages.PursueDesiredState
    }

    case msg @ MainActor.Messages.ProjectDeleted(id) => withVerboseErrorHandler(msg) {
      searchActor ! SearchActor.Messages.SyncProject(id)
    }

    case msg @ MainActor.Messages.ProjectSync(id) => withVerboseErrorHandler(msg) {
      upsertProjectActor(id) // Start the project actor
      upsertSupervisorActor(id) ! SupervisorActor.Messages.PursueDesiredState
      searchActor ! SearchActor.Messages.SyncProject(id)
    }

    case msg @ MainActor.Messages.Scale(projectId, diffs) => withVerboseErrorHandler(msg) {
      upsertProjectActor(projectId) ! ProjectActor.Messages.Scale(diffs)
    }

    case msg @ MainActor.Messages.ShaCreated(projectId, id) => withVerboseErrorHandler(msg) {
      upsertSupervisorActor(projectId) ! SupervisorActor.Messages.PursueDesiredState
    }

    case msg @ MainActor.Messages.ShaUpdated(projectId, id) => withVerboseErrorHandler(msg) {
      upsertSupervisorActor(projectId) ! SupervisorActor.Messages.PursueDesiredState
    }

    case msg @ MainActor.Messages.TagCreated(projectId, id, name) => withVerboseErrorHandler(msg) {
      upsertSupervisorActor(projectId) ! SupervisorActor.Messages.CheckTag(name)
    }

    case msg @ MainActor.Messages.TagUpdated(projectId, id, name) => withVerboseErrorHandler(msg) {
      upsertSupervisorActor(projectId) ! SupervisorActor.Messages.CheckTag(name)
    }

    case msg @ MainActor.Messages.ImageCreated(projectId, id, version) => withVerboseErrorHandler(msg) {
      upsertSupervisorActor(projectId) ! SupervisorActor.Messages.CheckTag(version)
    }

    case msg @ MainActor.Messages.BuildDockerImage(projectId, version) => withVerboseErrorHandler(msg) {
      upsertDockerHubActor(projectId) ! DockerHubActor.Messages.Build(version)
    }

    case msg @ MainActor.Messages.CheckLastState(projectId) => withVerboseErrorHandler(msg) {
      upsertProjectActor(projectId) ! ProjectActor.Messages.CheckLastState
    }

    case msg @ MainActor.Messages.ProjectDesiredStateUpdated(projectId) => withVerboseErrorHandler(msg) {
      upsertSupervisorActor(projectId) ! SupervisorActor.Messages.PursueDesiredState
    }

    case msg @ MainActor.Messages.ProjectLastStateUpdated(projectId) => withVerboseErrorHandler(msg) {
      upsertSupervisorActor(projectId) ! SupervisorActor.Messages.PursueDesiredState
    }

    case msg: Any => logUnhandledMessage(msg)

  }

  def upsertDockerHubActor(projectId: String): ActorRef = {
    dockerHubActors.lift(projectId).getOrElse {
      val ref = Akka.system.actorOf(Props[DockerHubActor], name = s"$name:dockerHubActor:$projectId")
      ref ! DockerHubActor.Messages.Data(projectId)
      dockerHubActors += (projectId -> ref)
      ref
    }
  }

  def upsertUserActor(id: String): ActorRef = {
    userActors.lift(id).getOrElse {
      val ref = Akka.system.actorOf(Props[UserActor], name = s"$name:userActor:$id")
      ref ! UserActor.Messages.Data(id)
      userActors += (id -> ref)
      ref
    }
  }

  def upsertProjectActor(id: String): ActorRef = {
    projectActors.lift(id).getOrElse {
      val ref = Akka.system.actorOf(Props[ProjectActor], name = s"$name:projectActor:$id")
      ref ! ProjectActor.Messages.Data(id)
      projectActors += (id -> ref)
      ref
    }
  }

  def upsertSupervisorActor(id: String): ActorRef = {
    supervisorActors.lift(id).getOrElse {
      val ref = Akka.system.actorOf(Props[SupervisorActor], name = s"$name:supervisorActor:$id")
      ref ! SupervisorActor.Messages.Data(id)
      supervisorActors += (id -> ref)
      ref
    }
  }
}
