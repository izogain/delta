package db

import io.flow.delta.v0.models.{StateForm, Version}
import io.flow.postgresql.Authorization
import org.scalatest._
import play.api.test._
import play.api.test.Helpers._
import org.scalatestplus.play._
import java.util.UUID

class ProjectExpectedStatesDaoSpec extends PlaySpec with OneAppPerSuite with Helpers {

  import scala.concurrent.ExecutionContext.Implicits.global

  def createStateForm(): StateForm = {
    StateForm(
      versions = Seq(
        Version(name = "0.0.1", instances = 3),
        Version(name = "0.0.2", instances = 2)
      )
    )
  }

  "create" in {
    val project = createProject()
    val state = rightOrErrors(ProjectExpectedStatesDao.create(systemUser, project, createStateForm()))
    state.versions.map(_.name) must be(Seq("0.0.1", "0.0.2"))
    state.versions.map(_.instances) must be(Seq(3, 2))
  }

  "upsert" in {
    val project = createProject()
    val state = rightOrErrors(ProjectExpectedStatesDao.upsert(systemUser, project, createStateForm()))
    val second = rightOrErrors(ProjectExpectedStatesDao.upsert(systemUser, project, createStateForm()))
    second.versions.map(_.name) must be(Seq("0.0.1", "0.0.2"))
    state.versions.map(_.instances) must be(Seq(3, 2))
  }

  "upsert" in {
    val project = createProject()
    val state = rightOrErrors(ProjectExpectedStatesDao.upsert(systemUser, project, createStateForm()))
    val second = rightOrErrors(ProjectExpectedStatesDao.upsert(systemUser, project, createStateForm()))
    state.versions.map(_.name) must be(Seq("0.0.1", "0.0.2"))
    state.versions.map(_.instances) must be(Seq(3, 2))
  }

  /*
  "upsertMaster" in {
    val project = createProject()

    val hash = createTestKey()

    val sha = ProjectExpectedStatesDao.upsertMaster(systemUser, project.id, hash)
    sha.hash must be(hash)

    val sha2 = ProjectExpectedStatesDao.upsertMaster(systemUser, project.id, hash)
    sha2.hash must be(hash)

    val other = createTestKey()
    val sha3 = ProjectExpectedStatesDao.upsertMaster(systemUser, project.id, other)
    sha3.hash must be(other)
  }

  "delete" in {
    val sha = createProjectExpectedState()
    ProjectExpectedStatesDao.delete(systemUser, sha)
    ProjectExpectedStatesDao.findById(Authorization.All, sha.id) must be(None)
  }

  "findById" in {
    val sha = createProjectExpectedState()
    ProjectExpectedStatesDao.findById(Authorization.All, sha.id).map(_.id) must be(
      Some(sha.id)
    )

    ProjectExpectedStatesDao.findById(Authorization.All, UUID.randomUUID.toString) must be(None)
  }

  "findByProjectIdAndBranch" in {
    val project = createProject()

    val masterForm = createProjectExpectedStateForm(project).copy(branch = "master")
    val master = rightOrErrors(ProjectExpectedStatesDao.create(systemUser, masterForm))

    val fooForm = createProjectExpectedStateForm(project).copy(branch = "foo")
    val foo = rightOrErrors(ProjectExpectedStatesDao.create(systemUser, fooForm))

    ProjectExpectedStatesDao.findByProjectIdAndBranch(Authorization.All, project.id, "master").map(_.hash) must be(Some(masterForm.hash))
    ProjectExpectedStatesDao.findByProjectIdAndBranch(Authorization.All, project.id, "foo").map(_.hash) must be(Some(fooForm.hash))
    ProjectExpectedStatesDao.findByProjectIdAndBranch(Authorization.All, project.id, "other") must be(None)
  }

  "findAll by ids" in {
    val sha1 = createProjectExpectedState()
    val sha2 = createProjectExpectedState()

    ProjectExpectedStatesDao.findAll(Authorization.All, ids = Some(Seq(sha1.id, sha2.id))).map(_.id).sorted must be(
      Seq(sha1.id, sha2.id).sorted
    )

    ProjectExpectedStatesDao.findAll(Authorization.All, ids = Some(Nil)) must be(Nil)
    ProjectExpectedStatesDao.findAll(Authorization.All, ids = Some(Seq(UUID.randomUUID.toString))) must be(Nil)
    ProjectExpectedStatesDao.findAll(Authorization.All, ids = Some(Seq(sha1.id, UUID.randomUUID.toString))).map(_.id) must be(Seq(sha1.id))
  }

  "findAll by projectId" in {
    val project1 = createProject()
    val project2 = createProject()

    val sha1 = createProjectExpectedState(createProjectExpectedStateForm(project1))
    val sha2 = createProjectExpectedState(createProjectExpectedStateForm(project2))

    ProjectExpectedStatesDao.findAll(Authorization.All, projectId = Some(project1.id)).map(_.id).sorted must be(
      Seq(sha1.id)
    )

    ProjectExpectedStatesDao.findAll(Authorization.All, projectId = Some(project2.id)).map(_.id).sorted must be(
      Seq(sha2.id)
    )

    ProjectExpectedStatesDao.findAll(Authorization.All, projectId = Some(createTestKey())) must be(Nil)
  }

  "validate" must {

    "require sha" in {
      ProjectExpectedStatesDao.validate(systemUser, createProjectExpectedStateForm().copy(hash = "   ")) must be(
        Seq("Hash cannot be empty")
      )
    }

    "require branch" in {
      ProjectExpectedStatesDao.validate(systemUser, createProjectExpectedStateForm().copy(branch = "   ")) must be(
        Seq("Branch cannot be empty")
      )
    }

    "validate project exists" in {
      ProjectExpectedStatesDao.validate(systemUser, createProjectExpectedStateForm().copy(projectId = createTestKey())) must be(
        Seq("Project not found")
      )
    }

    "validate user has access to the project's org" in {
      val org = createOrganization()
      val project = createProject(org)
      val orgMember = createUser()
      createMembership(createMembershipForm(org = org, user = orgMember))

      ProjectExpectedStatesDao.validate(orgMember, createProjectExpectedStateForm(project)) must be(Nil)
      ProjectExpectedStatesDao.validate(createUser(), createProjectExpectedStateForm(project)) must be(
        Seq("User does not have access to this organization")
      )
    }

    "validate existing record" in {
      val form = createProjectExpectedStateForm()
      val sha = createProjectExpectedState(form)

      ProjectExpectedStatesDao.validate(systemUser, form) must be(
        Seq("Project already has a hash for this branch")
      )

      ProjectExpectedStatesDao.validate(systemUser, form.copy(branch = createTestKey())) must be(Nil)
    }

  }

  "authorization for shas" in {
    val org = createOrganization()
    val project = createProject(org)
    val user = createUser()
    createMembership(createMembershipForm(org = org, user = user))

    val sha = createProjectExpectedState(createProjectExpectedStateForm(project), user = user)

    ProjectExpectedStatesDao.findAll(Authorization.PublicOnly, ids = Some(Seq(sha.id))) must be(Nil)
    ProjectExpectedStatesDao.findAll(Authorization.All, ids = Some(Seq(sha.id))).map(_.id) must be(Seq(sha.id))
    ProjectExpectedStatesDao.findAll(Authorization.Organization(org.id), ids = Some(Seq(sha.id))).map(_.id) must be(Seq(sha.id))
    ProjectExpectedStatesDao.findAll(Authorization.Organization(createOrganization().id), ids = Some(Seq(sha.id))) must be(Nil)
    ProjectExpectedStatesDao.findAll(Authorization.User(user.id), ids = Some(Seq(sha.id))).map(_.id) must be(Seq(sha.id))
    ProjectExpectedStatesDao.findAll(Authorization.User(createUser().id), ids = Some(Seq(sha.id))) must be(Nil)
  }
 */
}
