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
    TagsDao.findAll(
      Authorization.All,
      projectId = Some(project.id),
      orderBy = OrderBy("-tags.sort_key"),
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
            // By default, we create the same number of instances of
            // the new version as the total number of instances in the
            // last state.
            val instances: Long = state.versions.headOption.map(_.instances).sum match {
              case 0 => SetDesiredState.DefaultNumberInstances
              case n => n
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
