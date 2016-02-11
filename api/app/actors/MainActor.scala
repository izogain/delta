package io.flow.delta.actors

import db.{Authorization, BinaryVersionsDao, LibraryVersionsDao}
import io.flow.play.util.DefaultConfig
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

    case class ProjectCreated(id: String)
    case class ProjectUpdated(id: String)
    case class ProjectDeleted(id: String)
    case class ProjectSync(id: String)

    case class ProjectLibraryCreated(projectId: String, id: String)
    case class ProjectLibrarySync(projectId: String, id: String)
    case class ProjectLibraryDeleted(projectId: String, id: String)

    case class ProjectBinaryCreated(projectId: String, id: String)
    case class ProjectBinarySync(projectId: String, id: String)
    case class ProjectBinaryDeleted(projectId: String, id: String)

    case class ResolverCreated(id: String)
    case class ResolverDeleted(id: String)

    case class LibraryCreated(id: String)
    case class LibraryDeleted(id: String)
    case class LibrarySync(id: String)
    case class LibrarySyncFuture(id: String, seconds: Int)
    case class LibrarySyncCompleted(id: String)

    case class LibraryVersionCreated(id: String)
    case class LibraryVersionDeleted(id: String)

    case class BinaryCreated(id: String)
    case class BinaryDeleted(id: String)
    case class BinarySync(id: String)
    case class BinarySyncCompleted(id: String)

    case class BinaryVersionCreated(id: String)
    case class BinaryVersionDeleted(id: String)
    
    case class UserCreated(id: String)
  }
}


class MainActor(name: String) extends Actor with ActorLogging with Util {
  import scala.concurrent.duration._

  private[this] val emailActor = Akka.system.actorOf(Props[EmailActor], name = s"$name:emailActor")
  private[this] val periodicActor = Akka.system.actorOf(Props[PeriodicActor], name = s"$name:periodicActor")
  private[this] val searchActor = Akka.system.actorOf(Props[SearchActor], name = s"$name:SearchActor")

  private[this] val binaryActors = scala.collection.mutable.Map[String, ActorRef]()
  private[this] val libraryActors = scala.collection.mutable.Map[String, ActorRef]()
  private[this] val projectActors = scala.collection.mutable.Map[String, ActorRef]()
  private[this] val userActors = scala.collection.mutable.Map[String, ActorRef]()
  private[this] val resolverActors = scala.collection.mutable.Map[String, ActorRef]()

  implicit val mainActorExecutionContext: ExecutionContext = Akka.system.dispatchers.lookup("main-actor-context")

  /**
   * Helper to schedule a message to be sent on a recurring interval
   * based on a configuration parameter.
   *
   * @param configName The name of the configuration parameter containing the number
   *        of seconds between runs. You can also optionally add a configuration
   *        parameter of the same name with "_inital" appended to set the initial
   *        interval if you wish it to be different.
   */
  private[this] def scheduleRecurring[T](
    actor: ActorRef,
    configName: String,
    message: T
  ) {
    val seconds = DefaultConfig.requiredString(configName).toInt
    val initial = DefaultConfig.optionalString(s"${configName}_initial").map(_.toInt).getOrElse(seconds)
    Logger.info(s"scheduling a periodic message[$message]. Initial[$initial seconds], recurring[$seconds seconds]")
    Akka.system.scheduler.schedule(
      FiniteDuration(initial, SECONDS),
      FiniteDuration(seconds, SECONDS),
      actor, message
    )
  }

  scheduleRecurring(periodicActor, "io.flow.delta.api.binary.seconds", PeriodicActor.Messages.SyncBinaries)
  scheduleRecurring(periodicActor, "io.flow.delta.api.library.seconds", PeriodicActor.Messages.SyncLibraries)
  scheduleRecurring(periodicActor, "io.flow.delta.api.project.seconds", PeriodicActor.Messages.SyncProjects)
  scheduleRecurring(periodicActor, "io.flow.delta.api.purge.seconds", PeriodicActor.Messages.Purge)
  scheduleRecurring(emailActor, "io.flow.delta.api.email.seconds", EmailActor.Messages.ProcessDailySummary)

  def receive = akka.event.LoggingReceive {

    case msg @ MainActor.Messages.Configure(id) => withVerboseErrorHandler(msg) {
      projectActor ! ProjectActor.Messages.ConfigureECS(id) // One-time ECS setup
      projectActor ! ProjectActor.Messages.ConfigureEC2(id) // One-time EC2 setup
    }

    case m @ MainActor.Messages.UserCreated(id) => withVerboseErrorHandler(m) {
      upsertUserActor(id) ! UserActor.Messages.Created
    }

    case m @ MainActor.Messages.ProjectCreated(id) => withVerboseErrorHandler(m) {
      val actor = upsertProjectActor(id)
      actor ! ProjectActor.Messages.CreateHooks
      actor ! ProjectActor.Messages.Sync
      searchActor ! SearchActor.Messages.SyncProject(id)
    }

    case m @ MainActor.Messages.ProjectUpdated(id) => withVerboseErrorHandler(m) {
      upsertProjectActor(id) ! ProjectActor.Messages.Sync
      searchActor ! SearchActor.Messages.SyncProject(id)
    }

    case m @ MainActor.Messages.ProjectDeleted(id) => withVerboseErrorHandler(m) {
      projectActors.remove(id).map { actor =>
        actor ! ProjectActor.Messages.Deleted
      }
      searchActor ! SearchActor.Messages.SyncProject(id)
    }

    case m @ MainActor.Messages.ProjectSync(id) => withVerboseErrorHandler(m) {
      upsertProjectActor(id) ! ProjectActor.Messages.CreateHooks  // TODO Remove after all our projects have webhooks
      upsertProjectActor(id) ! ProjectActor.Messages.Sync
      searchActor ! SearchActor.Messages.SyncProject(id)
    }

    case m @ MainActor.Messages.ProjectLibraryCreated(projectId, id) => withVerboseErrorHandler(m) {
      upsertProjectActor(projectId) ! ProjectActor.Messages.ProjectLibraryCreated(id)
    }

    case m @ MainActor.Messages.ProjectLibrarySync(projectId, id) => withVerboseErrorHandler(m) {
      upsertProjectActor(projectId) ! ProjectActor.Messages.ProjectLibrarySync(id)
    }

    case m @ MainActor.Messages.ProjectLibraryDeleted(projectId, id) => withVerboseErrorHandler(m) {
      upsertProjectActor(projectId) ! ProjectActor.Messages.ProjectLibraryDeleted(id)
    }

    case m @ MainActor.Messages.ProjectBinaryCreated(projectId, id) => withVerboseErrorHandler(m) {
      upsertProjectActor(projectId) ! ProjectActor.Messages.ProjectBinaryCreated(id)
    }

    case m @ MainActor.Messages.ProjectBinarySync(projectId, id) => withVerboseErrorHandler(m) {
      upsertProjectActor(projectId) ! ProjectActor.Messages.ProjectBinarySync(id)
    }

    case m @ MainActor.Messages.ProjectBinaryDeleted(projectId, id) => withVerboseErrorHandler(m) {
      upsertProjectActor(projectId) ! ProjectActor.Messages.ProjectBinaryDeleted(id)
    }

    case m @ MainActor.Messages.LibraryCreated(id) => withVerboseErrorHandler(m) {
      syncLibrary(id)
    }

    case m @ MainActor.Messages.LibrarySync(id) => withVerboseErrorHandler(m) {
      syncLibrary(id)
    }

    case m @ MainActor.Messages.LibrarySyncFuture(id, seconds) => withVerboseErrorHandler(m) {
      Akka.system.scheduler.scheduleOnce(Duration(seconds, "seconds")) {
        println(s"MainActor.Messages.LibrarySyncFuture - $seconds - syncLibrary($id)")
        syncLibrary(id)
      }
    }
      
    case m @ MainActor.Messages.LibrarySyncCompleted(id) => withVerboseErrorHandler(m) {
      projectBroadcast(ProjectActor.Messages.LibrarySynced(id))
    }

    case m @ MainActor.Messages.LibraryDeleted(id) => withVerboseErrorHandler(m) {
      libraryActors.remove(id).map { ref =>
        ref ! LibraryActor.Messages.Deleted
      }
    }

    case m @ MainActor.Messages.LibraryVersionCreated(id) => withVerboseErrorHandler(m) {
      syncLibraryVersion(id)
    }

    case m @ MainActor.Messages.LibraryVersionDeleted(id) => withVerboseErrorHandler(m) {
      syncLibraryVersion(id)
    }

    case m @ MainActor.Messages.BinaryCreated(id) => withVerboseErrorHandler(m) {
      syncBinary(id)
    }

    case m @ MainActor.Messages.BinarySync(id) => withVerboseErrorHandler(m) {
      syncBinary(id)
    }

    case m @ MainActor.Messages.BinarySyncCompleted(id) => withVerboseErrorHandler(m) {
      projectBroadcast(ProjectActor.Messages.BinarySynced(id))
    }

    case m @ MainActor.Messages.BinaryDeleted(id) => withVerboseErrorHandler(m) {
      binaryActors.remove(id).map { ref =>
        ref ! BinaryActor.Messages.Deleted
      }
    }

    case m @ MainActor.Messages.BinaryVersionCreated(id) => withVerboseErrorHandler(m) {
      syncBinaryVersion(id)
    }

    case m @ MainActor.Messages.BinaryVersionDeleted(id) => withVerboseErrorHandler(m) {
      syncBinaryVersion(id)
    }

    case m @ MainActor.Messages.ResolverCreated(id) => withVerboseErrorHandler(m) {
      upsertResolverActor(id) ! ResolverActor.Messages.Sync
    }

    case m @ MainActor.Messages.ResolverDeleted(id) => withVerboseErrorHandler(m) {
      resolverActors.remove(id).map { ref =>
        ref ! ResolverActor.Messages.Deleted
      }
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

  def upsertProjectActor(id: String): ActorRef = {
    projectActors.lift(id).getOrElse {
      val ref = Akka.system.actorOf(Props[ProjectActor], name = s"$name:projectActor:$id")
      ref ! ProjectActor.Messages.Data(id)
      projectActors += (id -> ref)
      ref
    }
  }

  def upsertLibraryActor(id: String): ActorRef = {
    libraryActors.lift(id).getOrElse {
      val ref = Akka.system.actorOf(Props[LibraryActor], name = s"$name:libraryActor:$id")
      ref ! LibraryActor.Messages.Data(id)
      libraryActors += (id -> ref)
      ref
    }
  }

  def upsertBinaryActor(id: String): ActorRef = {
    binaryActors.lift(id).getOrElse {
      val ref = Akka.system.actorOf(Props[BinaryActor], name = s"$name:binaryActor:$id")
      ref ! BinaryActor.Messages.Data(id)
      binaryActors += (id -> ref)
      ref
    }
  }

  def upsertResolverActor(id: String): ActorRef = {
    resolverActors.lift(id).getOrElse {
      val ref = Akka.system.actorOf(Props[ResolverActor], name = s"$name:resolverActor:$id")
      ref ! ResolverActor.Messages.Data(id)
      resolverActors += (id -> ref)
      ref
    }
  }

  def syncLibrary(id: String) {
    upsertLibraryActor(id) ! LibraryActor.Messages.Sync
    searchActor ! SearchActor.Messages.SyncLibrary(id)
    projectBroadcast(ProjectActor.Messages.LibrarySynced(id))
  }


  def syncLibraryVersion(id: String) {
    LibraryVersionsDao.findAll(Authorization.All, id = Some(id), isDeleted = None).map { lv =>
      syncLibrary(lv.library.id)
    }
  }

  def syncBinary(id: String) {
    upsertBinaryActor(id) ! BinaryActor.Messages.Sync
    searchActor ! SearchActor.Messages.SyncBinary(id)
    projectBroadcast(ProjectActor.Messages.BinarySynced(id))
  }

  def syncBinaryVersion(id: String) {
    BinaryVersionsDao.findAll(Authorization.All, id = Some(id), isDeleted = None).map { bv =>
      syncBinary(bv.binary.id)
    }
  }

  def projectBroadcast(message: ProjectActor.Message) {
    projectActors.foreach { case (projectId, actor) =>
      actor ! message
    }
  }

}
