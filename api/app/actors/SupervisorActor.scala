package io.flow.delta.actors

import akka.actor.Actor
import db.{ProjectDesiredStatesDao, SettingsDao}
import io.flow.delta.api.lib.StateDiff
import io.flow.delta.v0.models.{Project, Settings, Version}
import io.flow.play.actors.ErrorHandler
import io.flow.postgresql.Authorization
import play.libs.Akka

object SupervisorActor {

  val StartedMessage = "started PursueDesiredState"
  
  trait Message

  object Messages {
    case class Data(id: String) extends Message

    case class CheckTag(name: String) extends Message
    case object PursueDesiredState extends Message
  }

  val All = Seq(
    functions.SyncMasterSha,
    functions.TagMaster,
    functions.SetDesiredState,
    functions.BuildDockerImage,
    functions.Scale
  )

}

class SupervisorActor extends Actor with ErrorHandler with DataProject with EventLog {

  private[this] implicit val supervisorActorExecutionContext = Akka.system.dispatchers.lookup("supervisor-actor-context")

  def receive = {

    case msg @ SupervisorActor.Messages.Data(id) => withVerboseErrorHandler(msg) {
      setProjectId(id)
    }

    /**
      * For any project that is not active (defined by not having an
      * event logged in last n seconds), we send a message to bring
      * that project to its desired state.
      */
    case msg @ SupervisorActor.Messages.PursueDesiredState => withVerboseErrorHandler(msg) {
      withProject { project =>
        val settings = SettingsDao.findByProjectIdOrDefault(Authorization.All, project.id)
        log.message(SupervisorActor.StartedMessage)
        run(project, settings, SupervisorActor.All)
        log.completed("PursueDesiredState")
      }
    }

    /**
      * Indicates that something has happened for the tag with
      * specified name (e.g. 0.0.2). If this tag is in the project's
      * desired state, triggers PursueDesiredState. Otherwise a
      * no-op.
      */
    case msg @ SupervisorActor.Messages.CheckTag(name: String) => withVerboseErrorHandler(msg) {
      withProject { project =>
        ProjectDesiredStatesDao.findByProjectId(Authorization.All, project.id).map { state =>
          StateDiff.up(state.versions, Seq(Version(name, 1))) match {
            case Nil => // This tag would not move the project forward
            case _ => self ! SupervisorActor.Messages.PursueDesiredState
          }
        }
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
            f.run(project).map { result =>
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

            }.recover {
              case ex: Throwable => log.completed(format(f, ex.getMessage), Some(ex))
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
      case "SetDesiredState" => settings.setDesiredState
      case "BuildDockerImage" => settings.buildDockerImage
      case "Scale" => settings.scale
      case other => sys.error(s"Cannot determine setting for function[$other]")
    }
  }

}
