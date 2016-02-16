package io.flow.delta.actors

import db.SettingsDao
import akka.actor.Actor
import io.flow.delta.v0.models.{Project, Settings}
import io.flow.play.actors.Util
import io.flow.postgresql.Authorization
import play.api.Logger
import play.libs.Akka
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.util.{Failure, Success, Try}

object SupervisorActor {

  val SuccessfulCompletionMessage = "completed PursueExpectedState"
  
  trait Message

  object Messages {
    case class Data(id: String) extends Message
    case object PursueExpectedState extends Message
  }

  val All = Seq(
    functions.SyncMasterSha,
    functions.TagMaster,
    functions.SetExpectedState,
    functions.BuildDockerImage,
    functions.Scale
  )

}

class SupervisorActor extends Actor with Util with DataProject with EventLog {

  override val logPrefix = "Supervisor"

  implicit val supervisorActorExecutionContext = Akka.system.dispatchers.lookup("supervisor-actor-context")

  def receive = {

    case msg @ SupervisorActor.Messages.Data(id) => withVerboseErrorHandler(msg) {
      setDataProject(id)
    }

    /**
      * For any project that is not active (defined by not having an
      * event logged in last n seconds), we send a message to bring
      * that project to its expected state.
      */
    case msg @ SupervisorActor.Messages.PursueExpectedState => withVerboseErrorHandler(msg) {
      withProject { project =>
        val settings = SettingsDao.findByProjectIdOrDefault(Authorization.All, project.id)
        log.started("PursueExpectedState")
        run(project, settings, SupervisorActor.All)
        log.message(SupervisorActor.SuccessfulCompletionMessage)
      }
    }

  }

  /**
    * Sequentially runs through the list of functions. If any of the
    * functions returns a SupervisorResult.Changed or
    * SupervisorResult.Error, returns that result. Otherwise will
    * return NoChange at the end of all the functions.
    */
  private[this] def run(project: Project, settings: Settings, functions: Seq[SupervisorFunction]) {
    functions.headOption match {
      case None => {
        SupervisorResult.NoChange("All functions returned without modification")
      }
      case Some(f) => {
        isEnabled(settings, f) match {
          case false => {
            log.skipped(format(f, "is disabled in the project settings"))
            run(project, settings, functions.drop(1))
          }
          case true => {
            log.started(format(f))
            Try(
              // TODO: Remove the await
              Await.result(
                f.run(project),
                Duration(10, "minutes")
              )
            ) match {
              case Success(result) => {
                result match {
                  case SupervisorResult.Change(desc) => {
                    log.changed(format(f, desc))
                  }
                  case SupervisorResult.NoChange(desc)=> {
                    log.completed(format(f, desc))
                    run(project, settings, functions.drop(1))
                  }
                  case SupervisorResult.Error(desc, ex)=> {
                    log.completed(format(f, desc), Some(ex))
                  }
                }
              }

              case Failure(ex) => {
                log.completed(format(f, ex.getMessage), Some(ex))
              }
            }
          }
        }
      }
    }
  }

  /**
    * Prepend the description with the class name of the
    * function. This lets us have automatic messages like
    * "TagMaster: xxx"
    */
  private[this] def format(f: Any, desc: String): String = {
    format(f) + ": " + desc
  }

  private[this] def format(f: Any): String = {
    val name = f.getClass.getName
    val idx = name.lastIndexOf(".")  // Remove classpath to just get function name
    name.substring(idx + 1).dropRight(1) // Remove trailing $
  }

  private[this] def isEnabled(settings: Settings, f: Any): Boolean = {
    format(f) match {
      case "SyncMasterSha" => settings.syncMasterSha
      case "TagMaster" => settings.tagMaster
      case "SetExpectedState" => settings.setExpectedState
      case "BuildDockerImage" => settings.buildDockerImage
      case "Scale" => settings.scale
      case other => sys.error(s"Cannot determine setting for function[$other]")
    }
  }

}
