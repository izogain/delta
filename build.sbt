import play.PlayImport.PlayKeys._

name := "delta"

organization := "io.flow"

scalaVersion in ThisBuild := "2.11.7"

// required because of issue between scoverage & sbt
parallelExecution in Test in ThisBuild := true

lazy val generated = project
  .in(file("generated"))
  .enablePlugins(PlayScala)
  .settings(commonSettings: _*)
  .settings(
    libraryDependencies ++= Seq(
      ws
    )
  )

lazy val lib = project
  .in(file("lib"))
  .dependsOn(generated)
  .aggregate(generated)
  .settings(commonSettings: _*)

lazy val api = project
  .in(file("api"))
  .dependsOn(generated, lib)
  .aggregate(generated, lib)
  .enablePlugins(PlayScala)
  .settings(commonSettings: _*)
  .settings(
    routesImport += "io.flow.delta.v0.Bindables._",
    routesGenerator := InjectedRoutesGenerator,
    libraryDependencies ++= Seq(
      ws,
      jdbc,
      "io.flow" %% "lib-postgresql" % "0.0.20",
      "com.typesafe.play" %% "anorm" % "2.5.0",
      "org.scalatestplus" %% "play" % "1.4.0" % "test",
      "com.amazonaws" % "aws-java-sdk" % "1.10.52",
      "org.postgresql" % "postgresql" % "9.4.1207",
      "com.sendgrid"   %  "sendgrid-java" % "2.2.2",
      "org.scalatestplus" %% "play" % "1.4.0" % "test"
    )
  )

lazy val www = project
  .in(file("www"))
  .dependsOn(generated, lib)
  .aggregate(generated, lib)
  .enablePlugins(PlayScala)
  .settings(commonSettings: _*)
  .settings(
    routesImport += "io.flow.delta.v0.Bindables._",
    routesGenerator := InjectedRoutesGenerator,
    libraryDependencies ++= Seq(
      "org.webjars" %% "webjars-play" % "2.4.0-2",
      "org.webjars" % "bootstrap" % "3.3.6",
      "org.webjars.bower" % "bootstrap-social" % "4.10.1",
      "org.webjars" % "font-awesome" % "4.5.0",
      "org.webjars" % "jquery" % "2.1.4"
    )
  )

val credsToUse = Option(System.getenv("ARTIFACTORY_USERNAME")) match {
  case None => Credentials(Path.userHome / ".ivy2" / ".artifactory")
  case _ => Credentials("Artifactory Realm","flow.artifactoryonline.com",System.getenv("ARTIFACTORY_USERNAME"),System.getenv("ARTIFACTORY_PASSWORD"))
}


lazy val commonSettings: Seq[Setting[_]] = Seq(
  name <<= name("delta-" + _),
  libraryDependencies ++= Seq(
    "io.flow" %% "lib-play" % "0.0.36",
    specs2 % Test,
    "org.scalatest" %% "scalatest" % "2.2.6" % "test"
  ),
  sources in (Compile,doc) := Seq.empty,
  publishArtifact in (Compile, packageDoc) := false,
  scalacOptions += "-feature",
  coverageHighlighting := true,
  resolvers += "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/",
  resolvers += "scalaz-bintray" at "https://dl.bintray.com/scalaz/releases",
  resolvers += "Artifactory" at "https://flow.artifactoryonline.com/flow/libs-release/",
  credentials += Credentials(
    "Artifactory Realm",
    "flow.artifactoryonline.com",
    System.getenv("ARTIFACTORY_USERNAME"),
    System.getenv("ARTIFACTORY_PASSWORD")
  )
)
