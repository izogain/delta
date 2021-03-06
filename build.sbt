import play.sbt.PlayScala._

name := "delta"

organization := "io.flow"

scalaVersion in ThisBuild := "2.12.7"

val awsVersion = "1.11.432"

lazy val generated = project
  .in(file("generated"))
  .enablePlugins(PlayScala)
  .enablePlugins(SbtWeb)
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
  .settings(
    libraryDependencies ++= Seq(
      "org.yaml" % "snakeyaml" % "1.23"
    )
  )

lazy val api = project
  .in(file("api"))
  .dependsOn(generated, lib)
  .aggregate(generated, lib)
  .enablePlugins(PlayScala)
  .enablePlugins(NewRelic)
  .enablePlugins(JavaAppPackaging, JavaAgent)
  .settings(commonSettings: _*)
  .settings(
    javaAgents += "org.aspectj" % "aspectjweaver" % "1.8.13",
    javaOptions in Universal += "-Dorg.aspectj.tracing.factory=default",
    javaOptions in Test += "-Dkamon.modules.kamon-system-metrics.auto-start=false",
    javaOptions in Test += "-Dkamon.show-aspectj-missing-warning=no",
    routesImport += "io.flow.delta.v0.Bindables.Core._",
    routesImport += "io.flow.delta.v0.Bindables.Models._",
    routesGenerator := InjectedRoutesGenerator,
    libraryDependencies ++= Seq(
      jdbc,
      "io.flow" %% "lib-postgresql-play-play26" % "0.2.61",
      "io.flow" %% "lib-event-play26" % "0.4.19",
      "com.amazonaws" % "aws-java-sdk-ec2" % awsVersion,
      "com.amazonaws" % "aws-java-sdk-ecs" % awsVersion,
      "com.amazonaws" % "aws-java-sdk-elasticloadbalancing" % awsVersion,
      "com.amazonaws" % "aws-java-sdk-autoscaling" % awsVersion,
      "com.amazonaws" % "aws-java-sdk-sns" % awsVersion,
      "com.sendgrid" %  "sendgrid-java" % "4.3.0",
      "org.postgresql" % "postgresql" % "42.2.5",
      "com.typesafe.play" %% "play-json-joda" % "2.6.10",
      "io.flow" %% "lib-play-graphite-play26" % "0.0.53",
      "io.flow" %% "lib-log" % "0.0.35"
    )
  )

lazy val www = project
  .in(file("www"))
  .dependsOn(generated, lib)
  .aggregate(generated, lib)
  .enablePlugins(PlayScala)
  .enablePlugins(NewRelic)
  .enablePlugins(SbtWeb)
  .settings(commonSettings: _*)
  .settings(
    routesImport += "io.flow.delta.v0.Bindables.Core._",
    routesImport += "io.flow.delta.v0.Bindables.Models._",
    routesGenerator := InjectedRoutesGenerator,
    libraryDependencies ++= Seq(
      "org.webjars" %% "webjars-play" % "2.6.3",
      "org.webjars" % "bootstrap" % "3.3.7",
      "org.webjars.bower" % "bootstrap-social" % "5.1.1",
      "org.webjars" % "font-awesome" % "5.4.1",
      "org.webjars" % "jquery" % "2.1.4"
    )
  )

val credsToUse = Option(System.getenv("ARTIFACTORY_USERNAME")) match {
  case None => Credentials(Path.userHome / ".ivy2" / ".artifactory")
  case _ => Credentials("Artifactory Realm","flow.jfrog.io",System.getenv("ARTIFACTORY_USERNAME"),System.getenv("ARTIFACTORY_PASSWORD"))
}


lazy val commonSettings: Seq[Setting[_]] = Seq(
  name ~= ("delta-" + _),
  libraryDependencies ++= Seq(
    ws,
    guice,
    "io.flow" %% "lib-play-play26" % "0.5.9",
    "io.flow" %% "lib-test-utils" % "0.0.18" % Test
  ),
  sources in (Compile,doc) := Seq.empty,
  publishArtifact in (Compile, packageDoc) := false,
  scalacOptions += "-feature",
  resolvers += "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/",
  resolvers += "scalaz-bintray" at "https://dl.bintray.com/scalaz/releases",
  resolvers += "Artifactory" at "https://flow.jfrog.io/flow/libs-release/",
  credentials += Credentials(
    "Artifactory Realm",
    "flow.jfrog.io",
    System.getenv("ARTIFACTORY_USERNAME"),
    System.getenv("ARTIFACTORY_PASSWORD")
  )
)
version := "0.6.94"
