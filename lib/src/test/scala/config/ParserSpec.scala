package io.flow.delta.lib.config

import io.flow.delta.config.v0.models.{Branch, Build, BuildStage, ConfigError, ConfigProject, ConfigUndefinedType, InstanceType, ProjectStage}
import org.specs2.mutable._

class ParserSpec extends Specification {

  private[this] lazy val parser = new Parser()

  def configProject(value: String): ConfigProject = {
    parser.parse(value) match {
      case c: ConfigProject => c
      case c: ConfigError => sys.error(s"Failed to parse config[${c.errors}]")
      case ConfigUndefinedType(other) => sys.error(s"Invalid project config[$other]")
    }
  }

  "Empty file" in {
    configProject("") must be(Defaults.Config)
    configProject("   ") must be(Defaults.Config)
  }

  "Invalid file" in {
    parser.parse("!@#$lkasdu4d") match {
      case ConfigError(errors) => errors.isEmpty must beFalse
      case _ => sys.error("No error when parsing invalid yaml")
    }
  }

  "project stages" in {
    configProject("").stages must be(ProjectStage.all)

    configProject("""
enable:
  - tag
    """).stages must beEqualTo(Seq(ProjectStage.Tag))

    configProject("""
disable:
  - tag
    """).stages must beEqualTo(Seq(ProjectStage.SyncShas, ProjectStage.SyncTags))
  }

  "Branches" in {
    configProject("""
branches:
  - master
    """) must beEqualTo(Defaults.Config)

    configProject("""
branches:
  - master
  - release
    """).branches.map(_.name) must beEqualTo(Seq("master", "release"))
  }

  "Builds" in {
    configProject("""
builds:
  - root
    """) must beEqualTo(Defaults.Config)

    configProject("""
builds:
  - api
  - www
    """).builds.toList match {
      case api :: www :: Nil => {
        api must beEqualTo(Build("api", "./Dockerfile", 2, InstanceType.T2Micro, BuildStage.all, Nil))
        www must beEqualTo(Build("www", "./Dockerfile", 2, InstanceType.T2Micro, BuildStage.all, Nil))
      }

      case _ => sys.error("Expected two branches")
    }

    configProject("""
builds:
  - api:
      dockerfile: api/Dockerfile    
      instance.type: t2.medium
      initial_number_instances: 5
      disable:
        - scale
  - www:
      dockerfile: www/Dockerfile
      initial_number_instances: 10
      enable:
        - set_desired_state
        - build_docker_image
      dependencies:
        - api
    """).builds.toList match {
      case api :: www :: Nil => {
        api must beEqualTo(
          Build("api", "api/Dockerfile", 5, InstanceType.T2Medium, Seq(BuildStage.SetDesiredState, BuildStage.BuildDockerImage), Nil)
        )
        www must beEqualTo(
          Build("www", "www/Dockerfile", 10, InstanceType.T2Micro, Seq(BuildStage.SetDesiredState, BuildStage.BuildDockerImage), Seq("api"))
        )
      }

      case _ => sys.error("Expected two branches")
    }
  }

}
