package io.flow.delta.lib

import org.specs2.mutable._

class BuildNamesSpec extends Specification {

  "dockerfilePathToBuildForm basics" in {
    val form = BuildNames.dockerfilePathToBuildForm("delta", "./Dockerfile")
    form.projectId must beEqualTo("delta")
    form.name must beEqualTo("root")
    form.dockerfilePath must beEqualTo("./Dockerfile")
  }

  "dockerfilePathToBuildForm names" in {
    BuildNames.dockerfilePathToBuildForm("delta", "/Dockerfile").name must beEqualTo("root")
    BuildNames.dockerfilePathToBuildForm("delta", "Dockerfile").name must beEqualTo("root")

    BuildNames.dockerfilePathToBuildForm("delta", "/api/Dockerfile").name must beEqualTo("api")
    BuildNames.dockerfilePathToBuildForm("delta", "./api/Dockerfile").name must beEqualTo("api")
    BuildNames.dockerfilePathToBuildForm("delta", "api/Dockerfile").name must beEqualTo("api")

    BuildNames.dockerfilePathToBuildForm("delta", "/www/Dockerfile").name must beEqualTo("www")
    BuildNames.dockerfilePathToBuildForm("delta", "./www/Dockerfile").name must beEqualTo("www")
    BuildNames.dockerfilePathToBuildForm("delta", "www/Dockerfile").name must beEqualTo("www")

    BuildNames.dockerfilePathToBuildForm("delta", "/Dockerfile.api").name must beEqualTo("api")
    BuildNames.dockerfilePathToBuildForm("delta", "/Dockerfile.www").name must beEqualTo("www")

    BuildNames.dockerfilePathToBuildForm("delta", "/a/b/c/Dockerfile").name must beEqualTo("a-b-c")
    BuildNames.dockerfilePathToBuildForm("delta", "/a/b/c/Dockerfile.api").name must beEqualTo("a-b-c-api")
  }
}
