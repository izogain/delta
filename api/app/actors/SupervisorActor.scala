package io.flow.delta.actors

import io.flow.postgresql.Authorization
import db.ProjectsDao
import io.flow.delta.v0.models.Project
import io.flow.play.actors.Util
import play.api.Logger
import play.libs.Akka
import akka.actor.Actor

object SupervisorActor {

  trait Message

  object Messages {
    case class Data(id: String) extends Message
    case object PursueExpectedState extends Message
  }

}

class SupervisorActor extends Actor with Util {

  implicit val supervisorActorExecutionContext = Akka.system.dispatchers.lookup("supervisor-actor-context")

  private[this] var supervisor: Option[Supervisor] = None

  def receive = {

    case msg @ SupervisorActor.Messages.Data(id) => withVerboseErrorHandler(msg) {
      supervisor = ProjectsDao.findById(Authorization.All, id).map { Supervisor(_) }
    }

    case msg @ SupervisorActor.Messages.PursueExpectedState => withVerboseErrorHandler(msg) {
      supervisor.foreach { supervisor =>
        println(s"Pursuing expected state for project: ${supervisor.project}")

        supervisor.captureMasterSha.map { result =>
          result match {
            case true => {
              println("==> New master sha created.")
              // we found next step. abort supervision
            }

            case false => {
              supervisor.tagIfNeeded.map { result =>
                result match {
                  case true => {
                    println("New tag created")
                  }
                  case false => {
                    println("==> No tag created - move on to next step")
                  }
                }
              }
            }
          }
        }
      }
    }

  }

}
