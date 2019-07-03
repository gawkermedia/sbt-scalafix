import sbt._

object Dependencies {
  val x = List(1) // scalafix:ok
  def scalafixVersion: String = sys.env.get("TRAVIS_TAG") match {
    case Some(v) if v.nonEmpty => v.stripPrefix("v")
    case _ => "0.9.5"
  }
  val all = List(
    "org.eclipse.jgit" % "org.eclipse.jgit" % "4.5.4.201711221230-r",
    "ch.epfl.scala" % "scalafix-interfaces" % scalafixVersion,
    "io.get-coursier" %% "coursier-cache" % "1.1.0-M6"
  )
}
