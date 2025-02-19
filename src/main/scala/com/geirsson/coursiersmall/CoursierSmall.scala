package com.geirsson.coursiersmall

import java.nio.file.Path
import coursier._
import coursier.ivy.{IvyRepository, Pattern}
import coursier.util.EitherT
import coursier.util.{Gather, Task}
import java.io.File
import scala.concurrent.ExecutionContext.Implicits.global

object CoursierSmall {

  /**
    * Resolve and fetch jars for the given settings.
    *
    * @param settings the configuration for this resolution.
    * @return list of paths to jar files on local disk containing the
    *         full classpath of the resolved dependencies.
    * @throws ResolutionError in case of a resolution error
    * @throws FileException   in case of problems caching files
    */
  def fetch(settings: Settings): List[Path] = {

    val dependencies = settings.dependencies.map { dep =>
      val split = dep.name.split(";")
      val name = split.head
      val attributes =
        for {
          attribute <- split.iterator.drop(1)
          Seq(key, value) = attribute.split("=", 2).toSeq
        } yield (key, value)
      coursier.Dependency(
        Module(dep.organization, name, attributes = attributes.toMap),
        dep.version,
        transitive = dep.transitive
      )
    }
    val forceVersions = settings.forceVersions.iterator.map { dep =>
      (Module(dep.organization, dep.name), dep.version)
    }
    val baseResolution = Resolution(
      rootDependencies = dependencies.toSet,
      forceVersions = forceVersions.toMap
    )
    val repositories = settings.repositories.map {
      case Repository.Ivy2Local =>
        Cache.ivy2Local
      case Repository.Ivy2Cache =>
        Cache.ivy2Cache
      case maven: Repository.Maven =>
        MavenRepository(maven.root).copy(authentication = maven.authentication)
      case ivy: Repository.Ivy =>
        IvyRepository.fromPattern(ivy.root +: Pattern.default).copy(authentication = ivy.authentication)
    }

    val term = new TermDisplay(settings.writer, fallbackMode = true)
    term.init()

    val cachePolicies = CachePolicy.default.toList
    val fetchs = cachePolicies.map { p =>
      Cache.fetch[Task](
        logger = Some(term),
        ttl = settings.ttl,
        cachePolicy = p
      )
    }
    val fetch = Fetch.from(
      repositories,
      fetchs.head,
      fetchs.tail: _*
    )
    val fetchResolution = baseResolution.process.run(fetch).unsafeRun()
    val errors = fetchResolution.errors
    if (errors.nonEmpty) {
      val resolutionErrors = errors.map {
        case ((module, version), messages) =>
          new ResolutionError(
            new Dependency(module.organization, module.name, version),
            messages.toList
          )
      }
      throw new ResolutionException(settings, resolutionErrors.toList)
    }

    val isDefaultClassifier =
      settings.classifiers.isEmpty ||
        settings.classifiers.contains("_")
    val baseArtifacts: Seq[Artifact] =
      if (isDefaultClassifier) fetchResolution.artifacts(withOptional = true)
      else Nil

    val nonDefaultClassifier = settings.classifiers.filterNot(_ == "_")
    val classifierArtifacts: Seq[Artifact] =
      if (nonDefaultClassifier.isEmpty) Nil
      else fetchResolution.classifiersArtifacts(nonDefaultClassifier)
    val artifacts = baseArtifacts ++ classifierArtifacts
    val localArtifacts = Gather[Task]
      .gather(artifacts.map { artifact =>
        def file(p: CachePolicy): EitherT[Task, FileError, File] =
          Cache.file[Task](artifact, ttl = settings.ttl, cachePolicy = p)
        (file(cachePolicies.head) /: cachePolicies.tail)(_ orElse file(_)).run
      })
      .unsafeRun()
      .zip(artifacts.map(_.isOptional))

    import com.geirsson.coursiersmall.{FileException => B}
    import coursier.{FileError => A}
    val jars = localArtifacts.flatMap {
      case (Left(A.NotFound(_, _)), true) =>
        Nil
      case (Left(e), _) =>
        throw e match {
          case A.DownloadError(reason) =>
            new B.DownloadError(reason)
          case A.NotFound(file, permanent) =>
            new B.NotFound(file, permanent)
          case A.Unauthorized(file, realm) =>
            new B.Unauthorized(file, realm)
          case A.ChecksumNotFound(sumType, file) =>
            new B.ChecksumNotFound(sumType, file)
          case A.ChecksumFormatError(sumType, file) =>
            new B.ChecksumFormatError(sumType, file)
          case A.WrongChecksum(sumType, got, expected, file, sumFile) =>
            new B.WrongChecksum(sumType, got, expected, file, sumFile)
          case A.Locked(file) =>
            new B.Locked(file.toPath)
          case A.ConcurrentDownload(url) =>
            new B.ConcurrentDownload(url)
        }
      case (Right(jar), _) if jar.getName.endsWith(".jar") => jar.toPath :: Nil
      case _ => Nil
    }
    term.stop()
    jars.toList
  }

}
