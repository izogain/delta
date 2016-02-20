package db

import org.scalatestplus.play._
import java.util.UUID

class ImagesDaoSpec extends PlaySpec with OneAppPerSuite with Helpers {

  lazy val imagesWriteDao = app.injector.instanceOf[ImagesWriteDao]

  "create" in {
    val project = createProject()
    val form = createImageForm(project)
    val image = rightOrErrors(imagesWriteDao.create(systemUser, form))
    image.project.id must be(project.id)
    image.name must be(form.name)
    image.version must be(form.version)

    imagesWriteDao.delete(systemUser, image)
    ImagesDao.findById(image.id) must be(None)
  }

  "upsert" in {
    val project = createProject()
    val name = "test"
    val version = "0.1.0"

    val image = imagesWriteDao.upsert(systemUser, project.id, name, version)
    image.name must be(name)
    image.version must be(version)

    val image2 = imagesWriteDao.upsert(systemUser, project.id, name, version)
    image2.id must be(image.id)
    image2.name must be(name)
    image2.version must be(version)

    val otherName = "test2"
    val image3 = imagesWriteDao.upsert(systemUser, project.id, otherName, version)
    image3.id must be(image2.id)
    image3.name must be(otherName)
    image3.version must be(version)

    imagesWriteDao.delete(systemUser, image)
    imagesWriteDao.delete(systemUser, image2)
    imagesWriteDao.delete(systemUser, image3)
  }

  "delete" in {
    val image = createImage()
    imagesWriteDao.delete(systemUser, image)
    ImagesDao.findById(image.id) must be(None)
  }

  "findById" in {
    val image = createImage()
    ImagesDao.findById(image.id).map(_.id) must be(
      Some(image.id)
    )

    ImagesDao.findById(UUID.randomUUID.toString) must be(None)

    imagesWriteDao.delete(systemUser, image)
  }

  "findByProjectIdAndVersion" in {
    val project = createProject()

    val form1 = createImageForm(project).copy(version = "0.0.2")
    val tag1 = rightOrErrors(imagesWriteDao.create(systemUser, form1))

    val form2 = createImageForm(project).copy(version = "0.0.3")
    val tag2 = rightOrErrors(imagesWriteDao.create(systemUser, form2))

    val image = ImagesDao.findByProjectIdAndVersion(project.id, "0.0.2")
    val image2 = ImagesDao.findByProjectIdAndVersion(project.id, "0.0.3")

    image.map(_.id) must be(Some(tag1.id))
    image2.map(_.id) must be(Some(tag2.id))
    ImagesDao.findByProjectIdAndVersion(project.id, "other") must be(None)

    imagesWriteDao.delete(systemUser, image.get)
    imagesWriteDao.delete(systemUser, image2.get)
  }

  "findAll by ids" in {
    val image1 = createImage()
    val image2 = createImage()

    ImagesDao.findAll(ids = Some(Seq(image1.id, image2.id))).map(_.id).sorted must be(
      Seq(image1.id, image2.id).sorted
    )

    ImagesDao.findAll(ids = Some(Nil)) must be(Nil)
    ImagesDao.findAll(ids = Some(Seq(UUID.randomUUID.toString))) must be(Nil)
    ImagesDao.findAll(ids = Some(Seq(image1.id, UUID.randomUUID.toString))).map(_.id) must be(Seq(image1.id))

    imagesWriteDao.delete(systemUser, image1)
    imagesWriteDao.delete(systemUser, image2)
  }

  "findAll by projectId" in {
    val project1 = createProject()
    val project2 = createProject()

    val image1 = createImage(createImageForm(project1))
    val image2 = createImage(createImageForm(project2))

    ImagesDao.findAll(projectId = Some(project1.id)).map(_.id).sorted must be(
      Seq(image1.id)
    )

    ImagesDao.findAll(projectId = Some(project2.id)).map(_.id).sorted must be(
      Seq(image2.id)
    )

    ImagesDao.findAll(projectId = Some(createTestKey())) must be(Nil)

    imagesWriteDao.delete(systemUser, image1)
    imagesWriteDao.delete(systemUser, image2)
  }

  "validate" must {
    "require version" in {
      imagesWriteDao.validate(systemUser, createImageForm().copy(version = "   ")) must be(
        Seq("Version cannot be empty")
      )
    }

    "require name" in {
      imagesWriteDao.validate(systemUser, createImageForm().copy(name = "   ")) must be(
        Seq("Name cannot be empty")
      )
    }

    "validate version is semver" in {
      imagesWriteDao.validate(systemUser, createImageForm().copy(version = "release")) must be(
        Seq("Version must match semver pattern (e.g. 0.1.2)")
      )
    }

    "validate project exists" in {
      imagesWriteDao.validate(systemUser, createImageForm().copy(projectId = createTestKey())) must be(
        Seq("Project not found")
      )
    }
  }
}
