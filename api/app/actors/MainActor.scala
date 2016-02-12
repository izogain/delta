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

    
    case class UserCreated(id: String)
  }
}


class MainActor(name: String) extends Actor with ActorLogging with Util {
  import scala.concurrent.duration._

  private[this] val searchActor = Akka.system.actorOf(Props[SearchActor], name = s"$name:SearchActor")

  private[this] val projectActors = scala.collection.mutable.Map[String, ActorRef]()
  private[this] val userActors = scala.collection.mutable.Map[String, ActorRef]()
  private[this] val imageActors = scala.collection.mutable.Map[String, ActorRef]()

  implicit val mainActorExecutionContext: ExecutionContext = Akka.system.dispatchers.lookup("main-actor-context")

  def receive = akka.event.LoggingReceive {

    case msg @ MainActor.Messages.Deploy(projectId, imageId) => withVerboseErrorHandler(msg) {
      upsertImageActor(projectId, imageId) ! ImageActor.Messages.Deploy
    }

    case m @ MainActor.Messages.UserCreated(id) => withVerboseErrorHandler(m) {
      upsertUserActor(id) ! UserActor.Messages.Created
    }

    case m @ MainActor.Messages.ProjectCreated(id) => withVerboseErrorHandler(m) {
      val actor = upsertProjectActor(id)
      actor ! ProjectActor.Messages.CreateHooks
      actor ! ProjectActor.Messages.ConfigureECS // One-time ECS setup
      actor ! ProjectActor.Messages.ConfigureEC2 // One-time EC2 setup
      actor ! ProjectActor.Messages.SyncGithub
      searchActor ! SearchActor.Messages.SyncProject(id)
    }

    case m @ MainActor.Messages.ProjectUpdated(id) => withVerboseErrorHandler(m) {
      upsertProjectActor(id) ! ProjectActor.Messages.SyncGithub
      searchActor ! SearchActor.Messages.SyncProject(id)
    }

    case m @ MainActor.Messages.ProjectDeleted(id) => withVerboseErrorHandler(m) {
      searchActor ! SearchActor.Messages.SyncProject(id)
    }

    case m @ MainActor.Messages.ProjectSync(id) => withVerboseErrorHandler(m) {
      upsertProjectActor(id) ! ProjectActor.Messages.SyncGithub
      searchActor ! SearchActor.Messages.SyncProject(id)
    }

    case m: Any => logUnhandledMessage(m)

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
    imageActors.lift(imageId).getOrElse {
      val ref = Akka.system.actorOf(Props[ImageActor], name = s"$name:imageActor:$imageId")
      ref ! ImageActor.Messages.Data(projectId, imageId)
      imageActors += (imageId -> ref)
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

}
