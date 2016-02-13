package io.flow.delta.actors

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

class SupervisorActor extends Actor with Util with DataProject {

  implicit val supervisorActorExecutionContext = Akka.system.dispatchers.lookup("supervisor-actor-context")

  def receive = {

    case msg @ SupervisorActor.Messages.Data(id) => withVerboseErrorHandler(msg) {
      setDataProject(id)
    }

    case msg @ SupervisorActor.Messages.PursueExpectedState => withVerboseErrorHandler(msg) {
      withProject { project =>
        println(s"SupervisorActor.Messages.PursueExpectedState Starting for Project[${project.id}]")
        run(project, SupervisorActor.All)
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
        println(s"==> Project[${project.id}] No change: All functions returned without modification")
        SupervisorResult.NoChange("All functions returned without modification")
      }
      case Some(f) => {
        val result = Await.result(f.run(project), Duration(5, "seconds"))
        result match {
          case SupervisorResult.Change(desc) => {
            println(s"==> Project[${project.id}] ${f.getClass.getName}: Changed $desc")
            SupervisorResult.Change(desc)
          }
          case SupervisorResult.NoChange(desc)=> {
            println(s"==> Project[${project.id}] ${f.getClass.getName}: No change: $desc")
            run(project, functions.drop(1))
          }
          case SupervisorResult.Error(desc, ex)=> {
            println(s"==> Project[${project.id}] ${f.getClass.getName}: $desc")
            ex.printStackTrace(System.err)
            SupervisorResult.Error(desc, ex)
          }
        }
      }
    }
  }

}
