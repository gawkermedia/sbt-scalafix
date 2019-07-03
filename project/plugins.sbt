resolvers := Seq(
        "Kinja Public Group" at sys.env.getOrElse("KINJA_PUBLIC_REPO", "https://kinjajfrog.jfrog.io/kinjajfrog/sbt-virtual"),
        "sonatype-releases" at "https://oss.sonatype.org/content/repositories/releases/"
)

credentials += Credentials(Path.userHome / ".ivy2" / ".kinja-artifactory.credentials")

addSbtPlugin("com.geirsson" % "sbt-ci-release" % "1.2.6")
addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.9.0")
addSbtPlugin(
  "io.get-coursier" % "sbt-coursier" % coursier.util.Properties.version
)

libraryDependencies += "org.scala-sbt" %% "scripted-plugin" % sbtVersion.value
unmanagedSourceDirectories.in(Compile) ++= {
  val root = baseDirectory.in(ThisBuild).value.getParentFile / "src" / "main"
  List(
    root / "scala",
    root / "scala-sbt-1.0"
  )
}
libraryDependencies ++= Dependencies.all
