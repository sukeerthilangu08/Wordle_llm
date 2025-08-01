val scala3Version = "3.3.1"

lazy val root = project
  .in(file("."))
  .settings(
    name := "wordle-solver",
    version := "0.1.0",
    scalaVersion := scala3Version,
    libraryDependencies ++= Seq(
      "com.softwaremill.sttp.client3" %% "core" % "3.9.0",
      "com.softwaremill.sttp.client3" %% "circe" % "3.9.0",
      "io.circe" %% "circe-core" % "0.14.5",
      "io.circe" %% "circe-generic" % "0.14.5",
      "io.circe" %% "circe-parser" % "0.14.5",
      "com.lihaoyi" %% "ujson" % "3.1.3"
    )
  )
