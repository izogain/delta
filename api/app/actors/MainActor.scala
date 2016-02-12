package actors

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

  object Messages {
    case class Configure(id: String)
    case class Deploy(id: String)
  }
}

class MainActor(name: String) extends Actor with Util {

  implicit val mainActorExecutionContext: ExecutionContext = Akka.system.dispatchers.lookup("main-actor-context")

  private[this] val imageActor = Akka.system.actorOf(Props[ImageActor], name = s"$name:ImageActor")
  private[this] val projectActor = Akka.system.actorOf(Props[ProjectActor], name = s"$name:ProjectActor")

  def receive = akka.event.LoggingReceive {
    case msg @ MainActor.Messages.Configure(id) => withVerboseErrorHandler(msg) {
      projectActor ! ProjectActor.Messages.ConfigureECS(id) // One-time ECS setup
      projectActor ! ProjectActor.Messages.ConfigureEC2(id) // One-time EC2 setup
    }

    case msg @ MainActor.Messages.Deploy(id) => withVerboseErrorHandler(msg) {
      imageActor ! ImageActor.Messages.Deploy(id)
    }
  }

}
