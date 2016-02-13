package io.flow.delta.actors

import io.flow.postgresql.Authorization
import db.ProjectsDao
import io.flow.delta.v0.models.Project
import io.flow.play.actors.Util
import play.api.Logger
import play.libs.Akka
import akka.actor.Actor
import scala.concurrent.Await
import scala.concurrent.duration.Duration

object SupervisorActor {

  trait Message

  object Messages {
    case class Data(id: String) extends Message
    case object PursueExpectedState extends Message
  }

  val All = Seq(
    functions.SyncMasterSha,
    functions.TagIfNeeded
  )

}


trait DataProject {

  private[this] var dataProject: Option[Project] = None

  /**
    * Looks up the project with the specified ID, setting the local
    * dataProject var to that project
    */
  def setDataProject(id: String) {
    dataProject = ProjectsDao.findById(Authorization.All, id)
    if (dataProject.isEmpty) {
      Logger.warn("Could not find project with id[$id]")
    }
  }

  /**
    * Invokes the specified function w/ the current project, but only
    * if we have a project set.
    */
  def withProject[T](f: Project => T) {
    dataProject.map { f(_) }
  }

}

class SupervisorActor extends Actor with Util with DataProject {

  implicit val supervisorActorExecutionContext = Akka.system.dispatchers.lookup("supervisor-actor-context")

  def receive = {

    case msg @ SupervisorActor.Messages.Data(id) => withVerboseErrorHandler(msg) {
      setDataProject(id)
    }

    case msg @ SupervisorActor.Messages.PursueExpectedState => withVerboseErrorHandler(msg) {
      println("SupervisorActor.Messages.PursueExpectedState")
      withProject { project =>
        val result = run(project, SupervisorActor.All)
        println("SupervisorActor.Messages.PursueExpectedState RESULT: " + result)
      }
    }

  }

  /**
    * Sequentially runs through the list of functions. If any of the
    * functions returns a SupervisorResult.Changed, returns that
    * instance. Otherwise will return Unchanged at the end of all the
    * functions.
    */
  private[this] def run(project: Project, functions: Seq[SupervisorFunction]): SupervisorResult = {
    functions.headOption match {
      case None => {
        SupervisorResult.NoChange("All functions returned without modification")
      }
      case Some(f) => {
        val result = Await.result(f.run(project), Duration(5, "seconds"))
        result match {
          case SupervisorResult.Change(desc) => {
            SupervisorResult.Change(desc)
          }
          case SupervisorResult.NoChange(desc)=> {
            // Run next function
            run(project, functions.drop(1))
          }
        }
      }
    }
  }

}
