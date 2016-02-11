package io.flow.delta.actors

import io.flow.delta.api.lib.{DefaultLibraryArtifactProvider, Dependencies, GithubDependencyProviderClient, GithubHelper, GithubUtil}
import io.flow.delta.v0.models.{Binary, BinaryForm, BinaryType, Library, LibraryForm, Project, ProjectBinary, ProjectLibrary, RecommendationType, VersionForm}
import io.flow.postgresql.Pager
import io.flow.play.util.DefaultConfig
import db.{Authorization, BinariesDao, LibrariesDao, LibraryVersionsDao, ProjectBinariesDao, ProjectLibrariesDao}
import db.{ProjectsDao, RecommendationsDao, SyncsDao, TokensDao, UsersDao}
import play.api.Logger
import play.libs.Akka
import akka.actor.Actor
import scala.concurrent.ExecutionContext

object ProjectActor {

  trait Message

  object Messages {
    case class Data(id: String) extends Message
    case object Deleted extends Message
    case object CreateHooks extends Message
    case object Sync extends Message
    case object SyncCompleted extends Message

    case class ProjectLibraryCreated(id: String) extends Message
    case class ProjectLibrarySync(id: String) extends Message
    case class ProjectLibraryDeleted(id: String) extends Message

    case class ProjectBinaryCreated(id: String) extends Message
    case class ProjectBinarySync(id: String) extends Message
    case class ProjectBinaryDeleted(id: String) extends Message

    case class LibrarySynced(id: String) extends Message
    case class BinarySynced(id: String) extends Message
  }

}

class ProjectActor extends Actor with Util {

  implicit val projectExecutionContext: ExecutionContext = Akka.system.dispatchers.lookup("project-actor-context")

  private[this] val HookBaseUrl = DefaultConfig.requiredString("dependency.api.host") + "/webhooks/github/"
  private[this] val HookName = "web"
  private[this] val HookEvents = Seq(io.flow.github.v0.models.HookEvent.Push)

  private[this] var dataProject: Option[Project] = None

  def receive = {

    case m @ ProjectActor.Messages.Data(id) => withVerboseErrorHandler(m.toString) {
      dataProject = ProjectsDao.findById(Authorization.All, id)
    }

    case m @ ProjectActor.Messages.ProjectLibraryCreated(id) => withVerboseErrorHandler(m.toString) {
      syncProjectLibrary(id)
    }

    case m @ ProjectActor.Messages.ProjectLibrarySync(id) => withVerboseErrorHandler(m.toString) {
      syncProjectLibrary(id)
    }

    case m @ ProjectActor.Messages.ProjectBinaryCreated(id) => withVerboseErrorHandler(m.toString) {
      syncProjectBinary(id)
    }

    case m @ ProjectActor.Messages.ProjectBinarySync(id) => withVerboseErrorHandler(m.toString) {
      syncProjectBinary(id)
    }

    case m @ ProjectActor.Messages.LibrarySynced(id) => withVerboseErrorHandler(m.toString) {
      dataProject.foreach { project =>
        processPendingSync(project)
      }
    }

    case m @ ProjectActor.Messages.BinarySynced(id) => withVerboseErrorHandler(m.toString) {
      dataProject.foreach { project =>
        processPendingSync(project)
      }
    }

    case m @ ProjectActor.Messages.CreateHooks => withVerboseErrorHandler(m.toString) {
      dataProject.foreach { project =>
        GithubUtil.parseUri(project.uri) match {
          case Left(error) => {
            Logger.warn(s"Project id[${project.id}] name[${project.name}]: $error")
          }
          case Right(repo) => {
            println(s"Create Hooks for project[${project.id}] repo[$repo]")
            UsersDao.findById(project.user.id).flatMap { u =>
              TokensDao.getCleartextGithubOauthTokenByUserId(u.id)
            } match {
              case None => {
                Logger.warn("No oauth token for user[${project.user.id}]")
              }
              case Some(token) => {
                println(s"Create Hooks for project[${project.id}] user[${project.user.id}] token[$token]")
                val client = GithubHelper.apiClient(token)

                client.hooks.get(repo.owner, repo.project).map { hooks =>
                  val targetUrl = HookBaseUrl + project.id
                  println(s"Got back from call to get targetUrl[$targetUrl]")

                  hooks.foreach { hook =>
                    println(s"hook id[${hook.id}] url[${hook.url}]")
                  }
                  hooks.find(_.config.url == Some(targetUrl)) match {
                    case Some(hook) => {
                      println("  - existing hook found: " + hook.id)
                      println("  - existing hook events: " + hook.events)
                    }
                    case None => {
                      println("  - hook not found. Creating")
                      println(s"  - HookEvents: ${HookEvents}")
                      client.hooks.post(
                        owner = repo.owner,
                        repo = repo.project,
                        name = HookName,
                        config = io.flow.github.v0.models.HookConfig(
                          url = Some(targetUrl),
                          contentType = Some("json")
                        ),
                        events = HookEvents,
                        active = true
                      )
                    }.map { hook =>
                      println("  - hook created: " + hook)
                    }.recover {
                      case e: Throwable => {
                        Logger.error("Project[${project.id}] Error creating hook: " + e)
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
    }

    case m @ ProjectActor.Messages.Sync => withVerboseErrorHandler(m.toString) {
      dataProject.foreach { project =>
        SyncsDao.recordStarted(MainActor.SystemUser, "project", project.id)

        UsersDao.findById(project.user.id).map { user =>
          val summary = ProjectsDao.toSummary(project)

          GithubDependencyProviderClient.instance(summary, user).dependencies(project).map { dependencies =>
            println(s" - project[${project.id}] name[${project.name}] dependencies: $dependencies")

            dependencies.binaries.map { binaries =>
              val projectBinaries = binaries.map { form =>
                println(s" -- project[${project.id}] name[${project.name}] binaries dao upsert")
                ProjectBinariesDao.upsert(user, form) match {
                  case Left(errors) => {
                    Logger.error(s"Project[${project.name}] id[${project.id}] Error storing binary[$form]: " + errors.mkString(", "))
                    None
                  }
                  case Right(projectBinary) => {
                    Some(projectBinary)
                  }
                }
              }
              ProjectBinariesDao.setIds(user, project.id, projectBinaries.flatten)
            }

            dependencies.librariesAndPlugins.map { libraries =>
              val projectLibraries = libraries.map { artifact =>
                println(s" -- project[${project.id}] name[${project.name}] artifact upsert: " + artifact)
                println(s" -- project[${project.id}] name[${project.name}] crossBuildVersion: " + dependencies.crossBuildVersion() + " binaries: " + dependencies.binaries)
                ProjectLibrariesDao.upsert(
                  user,
                  artifact.toProjectLibraryForm(
                    crossBuildVersion = dependencies.crossBuildVersion()
                  )
                ) match {
                  case Left(errors) => {
                    Logger.error(s"Project[${project.name}] id[${project.id}] Error storing artifact[$artifact]: " + errors.mkString(", "))
                    None
                  }
                  case Right(library) => {
                    Some(library)
                  }
                }
              }
              ProjectLibrariesDao.setIds(user, project.id, projectLibraries.flatten)
            }

            RecommendationsDao.sync(MainActor.SystemUser, project)

            processPendingSync(project)
          }.recover {
            case e => {
              e.printStackTrace(System.err)
              Logger.error(s"Error fetching dependencies for project[${project.id}] name[${project.name}]: $e")
            }
          }
        }
      }
    }

    case m @ ProjectActor.Messages.Deleted => withVerboseErrorHandler(m.toString) {
      dataProject.foreach { project =>
        Pager.create { offset =>
          RecommendationsDao.findAll(Authorization.All, projectId = Some(project.id), offset = offset)
        }.foreach { rec =>
          RecommendationsDao.softDelete(MainActor.SystemUser, rec)
        }
      }
      context.stop(self)
    }

    case m @ ProjectActor.Messages.ProjectLibraryDeleted(id) => withVerboseErrorHandler(m.toString) {
      dataProject.foreach { project =>
        ProjectLibrariesDao.findAll(
          Authorization.All,
          id = Some(id),
          isDeleted = Some(true)
        ).map { projectLibrary =>
          projectLibrary.library.map { lib =>
            RecommendationsDao.findAll(
              Authorization.All,
              projectId = Some(project.id),
              `type` = Some(RecommendationType.Library),
              objectId = Some(lib.id),
              fromVersion = Some(projectLibrary.version)
            ).foreach { rec =>
              RecommendationsDao.softDelete(MainActor.SystemUser, rec)
            }
          }
        }
        processPendingSync(project)
      }
    }

    case m @ ProjectActor.Messages.ProjectBinaryDeleted(id) => withVerboseErrorHandler(m.toString) {
      dataProject.foreach { project =>
        ProjectBinariesDao.findAll(
          Authorization.All,
          id = Some(id),
          isDeleted = Some(true)
        ).map { projectBinary =>
          projectBinary.binary.map { lib =>
            RecommendationsDao.findAll(
              Authorization.All,
              projectId = Some(project.id),
              `type` = Some(RecommendationType.Binary),
              objectId = Some(lib.id),
              fromVersion = Some(projectBinary.version)
            ).foreach { rec =>
              RecommendationsDao.softDelete(MainActor.SystemUser, rec)
            }
          }
        }
        processPendingSync(project)
      }
    }

    case m: Any => logUnhandledMessage(m)
  }

  /**
    * Attempts to resolve the library. If successful, sets the
    * project_libraries.library_id
    */
  def syncProjectLibrary(id: String) {
    SyncsDao.withStartedAndCompleted(MainActor.SystemUser, "project_library", id) {
      dataProject.foreach { project =>
        ProjectLibrariesDao.findById(Authorization.All, id).map { projectLibrary =>
          resolveLibrary(projectLibrary).map { lib =>
            ProjectLibrariesDao.setLibrary(MainActor.SystemUser, projectLibrary, lib)
          }
        }
        processPendingSync(project)
      }
    }
  }

  def syncProjectBinary(id: String) {
    SyncsDao.withStartedAndCompleted(MainActor.SystemUser, "project_binary", id) {
      dataProject.foreach { project =>
        ProjectBinariesDao.findById(Authorization.All, id).map { projectBinary =>
          resolveBinary(projectBinary).map { binary =>
            ProjectBinariesDao.setBinary(MainActor.SystemUser, projectBinary, binary)
          }
        }
        processPendingSync(project)
      }
    }
  }
  
  def processPendingSync(project: Project) {
    dependenciesPendingCompletion(project) match {
      case Nil => {
        println(s" -- project[${project.name}] id[${project.id}] dependencies satisfied")
        RecommendationsDao.sync(MainActor.SystemUser, project)
        SyncsDao.recordCompleted(MainActor.SystemUser, "project", project.id)
      }
      case deps => {
        println(s" -- project[${project.name}] id[${project.id}] waiting on dependencies to sync: " + deps.mkString(", "))
      }
    }
  }

  // NB: We don't return ALL dependencies
  private[this] def dependenciesPendingCompletion(project: Project): Seq[String] = {
    ProjectLibrariesDao.findAll(
      Authorization.All,
      projectId = Some(project.id),
      isSynced = Some(false)
    ).map( lib => s"Library ${lib.groupId}.${lib.artifactId}" ) ++ 
    ProjectBinariesDao.findAll(
      Authorization.All,
      projectId = Some(project.id),
      isSynced = Some(false)
    ).map( bin => s"Binary ${bin.name}" )
  }

  private[this] def resolveLibrary(projectLibrary: ProjectLibrary): Option[Library] = {
    LibrariesDao.findByGroupIdAndArtifactId(Authorization.All, projectLibrary.groupId, projectLibrary.artifactId) match {
      case Some(lib) => {
        Some(lib)
      }
      case None => {
        DefaultLibraryArtifactProvider().resolve(
          organization = projectLibrary.project.organization,
          groupId = projectLibrary.groupId,
          artifactId = projectLibrary.artifactId
        ) match {
          case None => {
            None
          }
          case Some(resolution) => {
            LibrariesDao.upsert(
              MainActor.SystemUser,
              form = LibraryForm(
                organizationId = projectLibrary.project.organization.id,
                groupId = projectLibrary.groupId,
                artifactId = projectLibrary.artifactId,
                resolverId = resolution.resolver.id
              )
            ) match {
              case Left(errors) => {
                Logger.error(s"Project[${projectLibrary.project.id}] name[${projectLibrary.project.name}] - error upserting library: " + errors.mkString(", "))
                None
              }
              case Right(library) => {
                Some(library)
              }
            }
          }
        }
      }
    }
  }

  private[this] def resolveBinary(projectBinary: ProjectBinary): Option[Binary] = {
    println(s"project id[${projectBinary.project.id}] projectBinaryCreated[${projectBinary.id}] name[${projectBinary.name}]")
    BinaryType(projectBinary.name) match {
      case BinaryType.Scala | BinaryType.Sbt => {
        BinariesDao.upsert(
          MainActor.SystemUser,
          BinaryForm(
            organizationId = projectBinary.project.organization.id,
            name = BinaryType(projectBinary.name)
          )
        ) match {
          case Left(errors) => {
            Logger.error(s"Project[${projectBinary.project.id}] name[${projectBinary.project.name}] - error upserting binary[$projectBinary]: " + errors.mkString(", "))
            None
          }
          case Right(binary) => {
            Some(binary)
          }
        }
      }
      case BinaryType.UNDEFINED(_) => {
        Logger.warn(s"Project[${projectBinary.id}] name[${projectBinary.name}] references an unknown binary[${projectBinary.name}]")
        None
      }
    }
  }

}
