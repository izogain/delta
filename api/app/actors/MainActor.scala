package io.flow.delta.actors

import io.flow.postgresql.Authorization
import io.flow.play.actors.Util
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

    case class Deploy(projectId: String, imageId: String)

    case class ProjectCreated(id: String)
    case class ProjectUpdated(id: String)
    case class ProjectDeleted(id: String)
    case class ProjectSync(id: String)

    case class ShaCreated(projectId: String, id: String)
    case class ShaUpdated(projectId: String, id: String)

    case class TagCreated(projectId: String, id: String)

    case class UserCreated(id: String)

    case class ImageCreated(id: String)
  }
}


class MainActor(name: String) extends Actor with ActorLogging with Util {
  import scala.concurrent.duration._

  private[this] val searchActor = Akka.system.actorOf(Props[SearchActor], name = s"$name:SearchActor")

  private[this] val deployImageActors = scala.collection.mutable.Map[String, ActorRef]()
  private[this] val dockerHubActors = scala.collection.mutable.Map[String, ActorRef]()
  private[this] val projectActors = scala.collection.mutable.Map[String, ActorRef]()
  private[this] val supervisorActors = scala.collection.mutable.Map[String, ActorRef]()
  private[this] val userActors = scala.collection.mutable.Map[String, ActorRef]()

  private[this] val periodicActor = Akka.system.actorOf(Props[PeriodicActor], name = s"$name:periodicActor")
  scheduleRecurring(periodicActor, "io.flow.delta.api.CheckProjects.seconds", PeriodicActor.Messages.CheckProjects)

  implicit val mainActorExecutionContext: ExecutionContext = Akka.system.dispatchers.lookup("main-actor-context")


  def receive = akka.event.LoggingReceive {

    case msg @ MainActor.Messages.Deploy(projectId, imageId) => withVerboseErrorHandler(msg) {
      upsertImageActor(projectId, imageId) ! DeployImageActor.Messages.Deploy
    }

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
      upsertSupervisorActor(id) ! SupervisorActor.Messages.PursueExpectedState
      searchActor ! SearchActor.Messages.SyncProject(id)
    }

    case msg @ MainActor.Messages.ProjectUpdated(id) => withVerboseErrorHandler(msg) {
      searchActor ! SearchActor.Messages.SyncProject(id)
      upsertSupervisorActor(id) ! SupervisorActor.Messages.PursueExpectedState
    }

    case msg @ MainActor.Messages.ProjectDeleted(id) => withVerboseErrorHandler(msg) {
      searchActor ! SearchActor.Messages.SyncProject(id)
    }

    case msg @ MainActor.Messages.ProjectSync(id) => withVerboseErrorHandler(msg) {
      upsertSupervisorActor(id) ! SupervisorActor.Messages.PursueExpectedState
      searchActor ! SearchActor.Messages.SyncProject(id)
    }

    case msg @ MainActor.Messages.ShaCreated(projectId, id) => withVerboseErrorHandler(msg) {
      upsertSupervisorActor(projectId) ! SupervisorActor.Messages.PursueExpectedState
    }

    case msg @ MainActor.Messages.ShaUpdated(projectId, id) => withVerboseErrorHandler(msg) {
      upsertSupervisorActor(projectId) ! SupervisorActor.Messages.PursueExpectedState
    }

    case msg @ MainActor.Messages.TagCreated(projectId, id) => withVerboseErrorHandler(msg) {
      upsertSupervisorActor(projectId) ! SupervisorActor.Messages.PursueExpectedState
    }

    case msg @ MainActor.Messages.ImageCreated(projectId) => withVerboseErrorHandler(msg) {
      upsertDockerHubActor(projectId) ! DockerHubActor.Messages.SyncImages
    }

    case msg: Any => logUnhandledMessage(msg)

  }

  def upsertDockerHubActor(id: String): ActorRef = {
    dockerHubActors.lift(id).getOrElse {
      val ref = Akka.system.actorOf(Props[DockerHubActor], name = s"$name:dockerHubActor:$id")
      ref ! DockerHubActor.Messages.Data(id)
      dockerHubActors += (id -> ref)
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

  /**
    * @param imageId e.g. "flowcommerce/user:0.0.1"
    */
  def upsertImageActor(projectId: String, imageId: String): ActorRef = {
    deployImageActors.lift(imageId).getOrElse {
      val ref = Akka.system.actorOf(Props[DeployImageActor], name = s"$name:deployImageActor:$imageId")
      ref ! DeployImageActor.Messages.Data(imageId)
      deployImageActors += (imageId -> ref)
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
