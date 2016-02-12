package io.flow.delta.actors

import java.util.UUID

import io.flow.delta.v0.models._
import io.flow.common.v0.models.User
import io.flow.play.actors.Util
import db._
import akka.actor.Actor
import io.flow.postgresql.Authorization
import scala.concurrent.ExecutionContext

object DockerHubActor {

  trait Message

  object Messages {
    case class Data(repo: Repository) extends Message
    case object Created extends Message
  }

}

class DockerHubActor extends Actor with Util {

 def receive = {

    case m @ DockerHubActor.Messages.Data(repo) => withVerboseErrorHandler(m.toString) {
      //ItemsDao.replace(MainActor.SystemUser, (repo.))
    }

    case m @ DockerHubActor.Messages.Created => withVerboseErrorHandler(m.toString) {

    }

    case m: Any => logUnhandledMessage(m)
  }



}


