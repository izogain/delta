package io.flow.delta.lib

import io.flow.delta.v0.models.{Build, BuildForm, DashboardBuild, Docker, Project}
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
    * /Dockerfile.www => "www"
    * /Dockerfile.api => "api"
    * /a/b/c/Dockerfile => "a-b-c"
    */
  def dockerfilePathToBuildForm(projectId: String, path: String): BuildForm = {
    val name = path.replace("Dockerfile.", "").replace("Dockerfile", "").split("/").filter(!_.startsWith(".")).filter(!_.isEmpty).toList match {
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
  def dockerImageName(docker: Docker, build: Build): String = {
    docker.organization + "/" + projectName(build)
  }

  def dockerImageName(docker: Docker, build: Build, version: String): String = {
    dockerImageName(docker, build) + s":$version"
  }

  /**
    * Given a build, returns the project name (without organization).
    * (e.g. registry or delta-api)
    */
  def projectName(build: Build): String = {
    projectName(build.project.name, build.name)
  }

  def projectName(dashboardBuild: DashboardBuild): String = {
    projectName(dashboardBuild.project.name, dashboardBuild.name)
  }
  
  private[this] def projectName(projectName: String, buildName: String): String = {
    buildName match {
      case DefaultBuildName => projectName
      case _ => projectName + "-" + buildName
    }
  }
  
}
