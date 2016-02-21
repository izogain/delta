package db

import io.flow.postgresql.Authorization
import org.scalatest._
import play.api.test._
import play.api.test.Helpers._
import org.scalatestplus.play._
import java.util.UUID

class BuildsDaoSpec extends PlaySpec with OneAppPerSuite with Helpers {

  import scala.concurrent.ExecutionContext.Implicits.global

  lazy val buildsWriteDao = app.injector.instanceOf[BuildsWriteDao]

  "create" in {
    val project = createProject()
    val dockerfilePath = "./Dockerfile"
    val form = createBuildForm(project).copy(name = "root", dockerfilePath = dockerfilePath)
    val build = rightOrErrors(buildsWriteDao.create(systemUser, form))
    build.project.id must be(project.id)
    build.name must be("root")
    build.dockerfilePath must be(dockerfilePath)
  }

  "delete" in {
    val build = createBuild()
    buildsWriteDao.delete(systemUser, build)
    BuildsDao.findById(Authorization.All, build.id) must be(None)
  }

  "findById" in {
    val build = createBuild()
    BuildsDao.findById(Authorization.All, build.id).map(_.id) must be(
      Some(build.id)
    )

    BuildsDao.findById(Authorization.All, UUID.randomUUID.toString) must be(None)
  }

  "findByProjectIdAndName" in {
    val project = createProject()

    val apiForm = createBuildForm(project).copy(name = "api")
    val api = rightOrErrors(buildsWriteDao.create(systemUser, apiForm))

    val wwwForm = createBuildForm(project).copy(name = "www")
    val www = rightOrErrors(buildsWriteDao.create(systemUser, wwwForm))

    BuildsDao.findByProjectIdAndName(Authorization.All, project.id, "api").map(_.dockerfilePath) must be(Some(apiForm.dockerfilePath))
    BuildsDao.findByProjectIdAndName(Authorization.All, project.id, "www").map(_.dockerfilePath) must be(Some(wwwForm.dockerfilePath))
    BuildsDao.findByProjectIdAndName(Authorization.All, project.id, "other") must be(None)
  }

  "findAll by ids" in {
    val build1 = createBuild()
    val build2 = createBuild()

    BuildsDao.findAll(Authorization.All, ids = Some(Seq(build1.id, build2.id))).map(_.id).sorted must be(
      Seq(build1.id, build2.id).sorted
    )

    BuildsDao.findAll(Authorization.All, ids = Some(Nil)) must be(Nil)
    BuildsDao.findAll(Authorization.All, ids = Some(Seq(UUID.randomUUID.toString))) must be(Nil)
    BuildsDao.findAll(Authorization.All, ids = Some(Seq(build1.id, UUID.randomUUID.toString))).map(_.id) must be(Seq(build1.id))
  }

  "findAll by projectId" in {
    val project1 = createProject()
    val project2 = createProject()

    val build1 = createBuild(createBuildForm(project1))
    val build2 = createBuild(createBuildForm(project2))

    BuildsDao.findAll(Authorization.All, projectId = Some(project1.id)).map(_.id).sorted must be(
      Seq(build1.id)
    )

    BuildsDao.findAll(Authorization.All, projectId = Some(project2.id)).map(_.id).sorted must be(
      Seq(build2.id)
    )

    BuildsDao.findAll(Authorization.All, projectId = Some(createTestKey())) must be(Nil)
  }

  "validate" must {

    "require dockerfile path" in {
      buildsWriteDao.validate(systemUser, createBuildForm().copy(dockerfilePath = "   ")) must be(
        Seq("Dockerfile path cannot be empty")
      )
    }

    "require name" in {
      buildsWriteDao.validate(systemUser, createBuildForm().copy(name = "   ")) must be(
        Seq("Name cannot be empty")
      )
    }

    "require name is a safe url key" in {
      buildsWriteDao.validate(systemUser, createBuildForm().copy(name = "flow!#$/commerce")) must be(
        Seq("Name must be in all lower case and contain alphanumerics only (-, _, and . are supported). A valid name would be: flow-commerce")
      )
    }

    "validate project exists" in {
      buildsWriteDao.validate(systemUser, createBuildForm().copy(projectId = createTestKey())) must be(
        Seq("Project not found")
      )
    }
 
    "validate existing record" in {
      val form = createBuildForm()
      val build = createBuild(form)

      buildsWriteDao.validate(systemUser, form) must be(
        Seq("Project already has a build with this name")
      )

      buildsWriteDao.validate(systemUser, form.copy(name = createTestKey())) must be(Nil)
    }

  }

  "authorization for builds" in {
    val org = createOrganization()
    val project = createProject(org)
    val user = createUser()
    createMembership(createMembershipForm(org = org, user = user))

    val build = createBuild(createBuildForm(project), user = user)

    BuildsDao.findAll(Authorization.PublicOnly, ids = Some(Seq(build.id))) must be(Nil)
    BuildsDao.findAll(Authorization.All, ids = Some(Seq(build.id))).map(_.id) must be(Seq(build.id))
    BuildsDao.findAll(Authorization.Organization(org.id), ids = Some(Seq(build.id))).map(_.id) must be(Seq(build.id))
    BuildsDao.findAll(Authorization.Organization(createOrganization().id), ids = Some(Seq(build.id))) must be(Nil)
    BuildsDao.findAll(Authorization.User(user.id), ids = Some(Seq(build.id))).map(_.id) must be(Seq(build.id))
    BuildsDao.findAll(Authorization.User(createUser().id), ids = Some(Seq(build.id))) must be(Nil)
  }

}
