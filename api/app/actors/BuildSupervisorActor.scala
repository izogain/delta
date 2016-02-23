package io.flow.delta.actors

import akka.actor.Actor
import db.{BuildsDao, BuildDesiredStatesDao, SettingsDao}
import io.flow.delta.api.lib.{BuildNames, StateDiff}
import io.flow.delta.v0.models.{Build, Settings, Version}
import io.flow.play.actors.ErrorHandler
import io.flow.postgresql.Authorization
import play.libs.Akka

object BuildSupervisorActor {

  trait Message

  object Messages {
    case class CheckTag(name: String) extends Message
    case class Data(id: String) extends Message
    case object PursueDesiredState extends Message
  }

  val Functions = Seq(
    functions.SetDesiredState,
    functions.BuildDockerImage,
    functions.Scale
  )

}

class BuildSupervisorActor extends Actor with ErrorHandler with DataBuild with EventLog {

  private[this] implicit val supervisorActorExecutionContext = Akka.system.dispatchers.lookup("supervisor-actor-context")

  override def logPrefix: String = {
    withBuild { build =>
      s"BuildSupervisorActor[${BuildNames.projectName(build)}]"
    }.getOrElse("BuildSupervisorActor[unknown build]")
  }

  def receive = {

    case msg @ BuildSupervisorActor.Messages.Data(id) => withVerboseErrorHandler(msg) {
      setBuildId(id)
    }

    case msg @ BuildSupervisorActor.Messages.PursueDesiredState => withVerboseErrorHandler(msg) {
      withBuild { build =>
        withSettings { settings =>
          log.runSync("PursueDesiredState") {
            run(build, settings, BuildSupervisorActor.Functions)
          }
        }
      }
    }

    /**
      * Indicates that something has happened for the tag with
      * specified name (e.g. 0.0.2). If this tag is in the build's
      * desired state, triggers PursueDesiredState. Otherwise a
      * no-op.
      */
    case msg @ BuildSupervisorActor.Messages.CheckTag(name) => withVerboseErrorHandler(msg) {  
      withBuild { build =>
        BuildDesiredStatesDao.findByBuildId(Authorization.All, build.id) match {
          case None => {
            // Might be first tag
            self ! BuildSupervisorActor.Messages.PursueDesiredState
          }
          case Some(state) => {
            StateDiff.up(state.versions, Seq(Version(name, 1))) match {
              case Nil => {
                state.versions.find(_.name == name) match {
                  case None => // no-op
                  case Some(_) => self ! BuildSupervisorActor.Messages.PursueDesiredState
                }
              }
              case _ => self ! BuildSupervisorActor.Messages.PursueDesiredState
            }
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
  private[this] def run(build: Build, settings: Settings, functions: Seq[BuildSupervisorFunction]) {
    functions.headOption match {
      case None => {
        SupervisorResult.NoChange("All functions returned without modification")
      }
      case Some(f) => {
        isEnabled(settings, f) match {
          case false => {
            log.skipped(format(f, "is disabled in the build settings"))
            run(build, settings, functions.drop(1))
          }
          case true => {
            log.started(format(f))
            f.run(build).map { result =>
              result match {
                case SupervisorResult.Change(desc) => {
                  log.changed(format(f, desc))
                }
                case SupervisorResult.NoChange(desc)=> {
                  log.completed(format(f, desc))
                  run(build, settings, functions.drop(1))
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

  private[this] def isEnabled(settings: Settings, f: Any): Boolean = {
    format(f) match {
      case "SetDesiredState" => settings.setDesiredState
      case "BuildDockerImage" => settings.buildDockerImage
      case "Scale" => settings.scale
      case other => sys.error(s"Cannot determine build setting for function[$other]")
    }
  }

}
