package io.flow.delta.actors.functions

import db.{ProjectDesiredStatesDao, TagsDao, UsersDao}
import io.flow.delta.actors.{SupervisorFunction, SupervisorResult}
import io.flow.delta.api.lib.StateFormatter
import io.flow.delta.v0.models.{Project, StateForm, Version}
import io.flow.postgresql.{Authorization, OrderBy}
import scala.concurrent.Future

/**
  * For projects that have auto deploy turned on, we set the desired
  * state to 100% of traffic on the latest tag.
  */
object SetDesiredState extends SupervisorFunction {

  val DefaultNumberInstances = 2

  override def run(
    project: Project
  ) (
    implicit ec: scala.concurrent.ExecutionContext
  ): Future[SupervisorResult] = {
    Future {
      SetDesiredState(project).run
    }
  }

}

case class SetDesiredState(project: Project) extends Github {

  def run(): SupervisorResult = {
    // TODO: Make sure tag is actually the latest in semver ordering.
    TagsDao.findAll(
      Authorization.All,
      projectId = Some(project.id),
      orderBy = OrderBy("-tags.created_at"),
      limit = 1
    ).headOption match {
      case None => {
        SupervisorResult.NoChange("Project does not have any tags")
      }

      case Some(latestTag) => {
        ProjectDesiredStatesDao.findByProjectId(Authorization.All, project.id) match {
          case None => {
            setVersions(Seq(Version(latestTag.name, instances = SetDesiredState.DefaultNumberInstances)))
          }
          case Some(state) => {
            // TODO: Maybe turn this into a sum? Not sure... The goal
            // here is to understand how many instance we should
            // create based on what we are already running.
            //  - If desired state was: 0.0.1 - 3 instances, and we
            //    are publishing 0.0.2, we want the new desired state
            //    to be 0.0.2 - 3 instances
            val instances: Long = state.versions.headOption.map(_.instances).getOrElse {
              SetDesiredState.DefaultNumberInstances
            }
            val targetVersions = Seq(Version(latestTag.name, instances = instances))

            if (state.versions == targetVersions) {
              SupervisorResult.NoChange("Desired state remains: " + StateFormatter.label(targetVersions))
            } else {
              setVersions(targetVersions)
            }
          }
        }
      }
    }
  }

  def setVersions(versions: Seq[Version]): SupervisorResult = {
    assert(!versions.isEmpty, "Must have at least one version")
    ProjectDesiredStatesDao.upsert(
      UsersDao.systemUser,
      project,
      StateForm(
        versions = versions
      )
    )
    SupervisorResult.Change("Desired state changed to: " + StateFormatter.label(versions))
  }

}
