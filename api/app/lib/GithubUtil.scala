package io.flow.delta.api.lib

object GithubUtil {

  def parseUri(uri: String): Either[String, Repo] = {
    Validation.validateUri(uri) match {
      case Left(errors) => {
        Left(errors.mkString(", "))
      }
      case Right(u) => {
        val path = if (u.getPath.startsWith("/")) {
          u.getPath.substring(1)
        } else {
          u.getPath
        }.trim
        path.split("/").filter(!_.isEmpty).toList match {
          case Nil => Left(s"URI path cannot be empty for uri[$uri]")
          case owner :: Nil => Left(s"Invalid uri path[$uri] missing project name")
          case owner :: project :: Nil => Right(Repo(owner, project))
          case _ => Left(s"Invalid uri path[$u] - expected exactly two path components")
        }
      }
    }
  }

}
