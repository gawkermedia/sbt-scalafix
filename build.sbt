inThisBuild(
  List(
    scalafixDependencies := List(
      // Custom rule published to Maven Central https://github.com/olafurpg/example-scalafix-rule
      "com.geirsson" %% "example-scalafix-rule" % "1.3.0"
    )
  )
)
onLoadMessage := s"Welcome to sbt-scalafix ${version.value}"
moduleName := "sbt-scalafix"

// Publish settings
organization := "com.kinja"
homepage := Some(url("https://github.com/scalacenter/sbt-scalafix"))
licenses := List(
  "Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")
)
version := "0.9.5-ext"

developers := List(
  Developer(
    "olafurpg",
    "Ólafur Páll Geirsson",
    "olafurpg@gmail.com",
    url("https://geirsson.com")
  )
)

commands += Command.command("ci-windows") { s =>
  "testOnly -- -l SkipWindows" ::
    "scripted" ::
    s
}

// Dependencies
resolvers += Resolver.sonatypeRepo("releases")
libraryDependencies ++= Dependencies.all
libraryDependencies ++= List(
  "com.lihaoyi" %% "fansi" % "0.2.5" % Test,
  "org.scalatest" %% "scalatest" % "3.0.5" % Test
)

// Cross-building settings (see https://github.com/sbt/sbt/issues/3473#issuecomment-325729747)
crossScalaVersions := Seq("2.10.7", "2.12.8")
sbtVersion in pluginCrossBuild := {
  scalaBinaryVersion.value match {
    case "2.10" => "0.13.17"
    case "2.12" => "1.2.1"
  }
}
libraryDependencies ++= {
  scalaBinaryVersion.value match {
    case "2.12" =>
      List(compilerPlugin(scalafixSemanticdb))
    case _ =>
      List()
  }
}
scalacOptions ++= {
  scalaBinaryVersion.value match {
    case "2.12" =>
      List("-Ywarn-unused", "-Yrangepos")
    case _ =>
      List()
  }
}
scalacOptions ++= List(
  "-target:jvm-1.8"
)

// Scripted
enablePlugins(ScriptedPlugin)
sbtPlugin := true
scriptedBufferLog := false
scriptedLaunchOpts ++= Seq(
  "-Xmx2048M",
  s"-Dplugin.version=${version.value}"
)

testOptions in Test --= Seq(Tests.Argument("showtimes", "true"), Tests.Argument("console"))
