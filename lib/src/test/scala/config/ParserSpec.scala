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
        api must beEqualTo(Defaults.Build.copy(name = "api"))
        www must beEqualTo(Defaults.Build.copy(name = "www"))
      }

      case _ => sys.error("Expected two branches")
    }

    configProject("""
builds:
  - api:
      dockerfile: api/Dockerfile    
      instance.type: t2.medium
      initial.number.instances: 5
      disable:
        - scale
  - www:
      dockerfile: www/Dockerfile
      initial.number.instances: 10
      enable:
        - set_desired_state
        - build_docker_image
      dependencies:
        - api
    """).builds.toList match {
      case api :: www :: Nil => {
        api must beEqualTo(
          Defaults.Build.copy(
            name = "api",
            dockerfile = "api/Dockerfile",
            initialNumberInstances = 5,
            instanceType = InstanceType.T2Medium,
            memory = 3500,
            stages = Seq(BuildStage.SetDesiredState, BuildStage.BuildDockerImage)
          )
        )
        www must beEqualTo(
          Defaults.Build.copy(
            name = "www",
            dockerfile = "www/Dockerfile",
            initialNumberInstances = 10,
            stages = Seq(BuildStage.SetDesiredState, BuildStage.BuildDockerImage),
            dependencies = Seq("api")
          )
        )
      }

      case _ => sys.error("Expected two branches")
    }

    configProject("""
builds:
  - root:
      instance.type: t2.medium
      memory: 1000
    """).builds.toList match {
      case build :: Nil => {
        build must beEqualTo(
          Defaults.Build.copy(instanceType = InstanceType.T2Medium, memory = 1000)
        )
      }

      case _ => sys.error("Expected two branches")
    }

    configProject("""
builds:
  - root:
      port.container: 9000
      port.host: 6021
    """).builds.toList match {
      case build :: Nil => {
        build must beEqualTo(
          Defaults.Build.copy(portContainer = 9000, portHost = 6021)
        )
      }

      case _ => sys.error("Expected two branches")
    }
  }

}
