package io.flow.delta.api.lib

import io.flow.delta.v0.models.{Build, BuildForm, Docker}
import io.flow.play.util.UrlKey

object BuildNames {

  private[this] val DefaultBuildName = "root"

  private[this] val urlKey = UrlKey(minKeyLength = 3)

  /**
    * Given the path to a dockerfile, returns the name of the build,
    * based on the directory structure.
    * 
    * ./Dockerfile => "root"
    * ./api/Dockerfile => "api"
    * www/Dockerfile => "www"
    */
  def dockerfilePathToBuildForm(projectId: String, path: String): BuildForm = {
    val name = path.split("/").dropRight(1).filter(!_.startsWith(".")).filter(!_.isEmpty).toList match {
      case Nil => DefaultBuildName
      case multiple => multiple.mkString("-")
    }

    BuildForm(
      projectId = projectId,
      name = urlKey.generate(name),
      dockerfilePath = path
    )
  }

  /**
    * Given a build, returns the full docker image name
    * (e.g. flowcommerce/delta-api)
    */
  def toDockerImageName(docker: Docker, build: Build): String = {
    docker.organization + "/" + toDockerImageName(build)
  }

  /**
    * Given a build, returns the image name (without organization).
    * (e.g. delta-api)
    */
  def toDockerImageName(build: Build): String = {
    build.name match {
      case DefaultBuildName => build.project.name
      case _ => build.project.name + "-" + build.name
    }
  }

}
