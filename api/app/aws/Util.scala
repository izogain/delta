package io.flow.delta.aws

import scala.concurrent.Future

object Util {

  case class DockerImage(org: String, project: String, version: String)

  // image name = "flow/user:0.0.1" - o = flow, p = user, version = 0.0.1
  // image name = "flow/delta-api:0.0.1" - o = flow, p = delta-api, version = 0.0.1
  private[this] val ImagePattern = """([^\/]+)/([^:]+):(.+)""".r


  def parseImage(name: String): Option[DockerImage] = {
    name match {
      case ImagePattern(o, p, version) => {
        Some(DockerImage(o, p, version))
      }
      case _ => {
        None
      }
    }
  }

}
