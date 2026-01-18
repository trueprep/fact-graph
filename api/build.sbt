val scala3Version = "3.3.6"

lazy val root = project
  .in(file("."))
  .settings(
    name := "factgraph-api",
    version := "1.0.0",
    scalaVersion := scala3Version,
    organization := "gov.irs",
    
    // Dependency on the local fact-graph
    libraryDependencies ++= Seq(
      "gov.irs" %% "factgraph" % "3.1.0-SNAPSHOT",
      
      // HTTP Server (http4s)
      "org.http4s" %% "http4s-ember-server" % "0.23.30",
      "org.http4s" %% "http4s-dsl" % "0.23.30",
      "org.http4s" %% "http4s-circe" % "0.23.30",
      
      // JSON serialization
      "io.circe" %% "circe-generic" % "0.14.10",
      "io.circe" %% "circe-parser" % "0.14.10",
      
      // Logging
      "ch.qos.logback" % "logback-classic" % "1.5.16"
    ),
    
    // Assembly settings for fat JAR
    assembly / assemblyMergeStrategy := {
      case PathList("META-INF", xs @ _*) => MergeStrategy.discard
      case "reference.conf" => MergeStrategy.concat
      case x => MergeStrategy.first
    }
  )