package io.flow.delta.actors

import akka.actor.Actor
import io.flow.delta.v0.models.Project
import io.flow.play.actors.Util
import play.api.Logger
import play.libs.Akka
import org.joda.time.DateTime
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.util.{Failure, Success, Try}

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
    * functions returns a SupervisorResult.Changed or
    * SupervisorResult.Error, returns that result. Otherwise will
    * return Unchanged at the end of all the functions.
    */
  private[this] def run(project: Project, functions: Seq[SupervisorFunction]): SupervisorResult = {
    functions.headOption match {
      case None => {
        val desc = "All functions returned without modification"
        println(msg(project, desc))
        SupervisorResult.NoChange(desc)
      }
      case Some(f) => {
        Try(
          // TODO: Remove the await
          Await.result(
            f.run(project),
            Duration(5, "seconds")
          )
        ) match {
          case Success(result) => {
            result match {
              case SupervisorResult.Change(desc) => {
                println(msg(project, f, desc))
                SupervisorResult.Change(desc)
              }
              case SupervisorResult.NoChange(desc)=> {
                println(msg(project, f, s"No change: $desc"))
                run(project, functions.drop(1))
              }
              case SupervisorResult.Error(desc, ex)=> {
                println(msg(project, f, s"Error: $desc"))
                ex.printStackTrace(System.err)
                SupervisorResult.Error(desc, ex)
              }
            }
          }

          case Failure(ex) => {
            val desc = s"Unhandled Exception ${ex.getMessage}"
            println(msg(project, f, desc))
            ex.printStackTrace(System.err)
            SupervisorResult.Error(desc, ex)
          }
        }
      }
    }
  }

  private[this] def msg(project: Project, f: Any, desc: String): String = {
    val name = f.getClass.getName
    val idx = name.lastIndexOf(".")  // Remove classpath to just get function name
    val formattedName = name.substring(idx + 1).dropRight(1) // Remove trailing $
    msg(project, s"$formattedName: $desc")
  }

  private[this] def msg(project: Project, desc: String): String = {
    val ts = new DateTime()
    s"==> $ts ${project.id}: $desc"
  }

}
