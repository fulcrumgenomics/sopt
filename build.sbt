import com.typesafe.sbt.SbtGit.GitCommand
import sbt.Keys._
import sbt._
import sbtassembly.AssemblyKeys.assembly
import sbtassembly.MergeStrategy
import sbtrelease.ReleasePlugin.autoImport.ReleaseTransformations._
import scoverage.ScoverageKeys._

////////////////////////////////////////////////////////////////////////////////////////////////
// We have the following "settings" in this build.sbt:
// - versioning with sbt-release
// - custom JAR name for the root project
// - settings to publish to Sonatype
// - exclude the root, tasks, and pipelines project from code coverage
// - scaladoc settings
// - custom merge strategy for assembly
////////////////////////////////////////////////////////////////////////////////////////////////

////////////////////////////////////////////////////////////////////////////////////////////////
// Use sbt-release to bump the version numbers.
//
// see: http://blog.byjean.eu/2015/07/10/painless-release-with-sbt.html
////////////////////////////////////////////////////////////////////////////////////////////////

// Release settings
releaseVersionBump := sbtrelease.Version.Bump.Next
releasePublishArtifactsAction := PgpKeys.publishSigned.value
releaseProcess := Seq[ReleaseStep](
  checkSnapshotDependencies,
  inquireVersions,
  runClean,
  runTest,
  setReleaseVersion,
  commitReleaseVersion,
  tagRelease,
  ReleaseStep(action = Command.process("publishSigned", _)),
  setNextVersion,
  commitNextVersion,
  ReleaseStep(action = Command.process("sonatypeReleaseAll", _)),
  pushChanges
)

////////////////////////////////////////////////////////////////////////////////////////////////
// For the aggregate (root) jar, override the name.  For the sub-projects,
// see the build.sbt in each project folder.
////////////////////////////////////////////////////////////////////////////////////////////////
assemblyJarName in assembly := "sopt-" + version.value + ".jar"

////////////////////////////////////////////////////////////////////////////////////////////////
// Sonatype settings
////////////////////////////////////////////////////////////////////////////////////////////////
publishMavenStyle := true
publishTo := {
  val nexus = "https://oss.sonatype.org/"
  if (isSnapshot.value)
    Some("snapshots" at nexus + "content/repositories/snapshots")
  else
    Some("releases"  at nexus + "service/local/staging/deploy/maven2")
}
publishArtifact in Test := false
pomIncludeRepository := { _ => false }
// For Travis CI - see http://www.cakesolutions.net/teamblogs/publishing-artefacts-to-oss-sonatype-nexus-using-sbt-and-travis-ci
credentials ++= (for {
  username <- Option(System.getenv().get("SONATYPE_USER"))
  password <- Option(System.getenv().get("SONATYPE_PASS"))
} yield Credentials("Sonatype Nexus Repository Manager", "oss.sonatype.org", username, password)).toSeq

////////////////////////////////////////////////////////////////////////////////////////////////
// Coverage settings: include all sources
////////////////////////////////////////////////////////////////////////////////////////////////
val htmlReportsDirectory: String = "target/test-reports"

////////////////////////////////////////////////////////////////////////////////////////////////
// scaladoc options
////////////////////////////////////////////////////////////////////////////////////////////////
val docScalacOptions = Seq("-groups", "-implicits")

////////////////////////////////////////////////////////////////////////////////////////////////
// Common settings for all projects
////////////////////////////////////////////////////////////////////////////////////////////////

lazy val commonSettings = Seq(
  organization         := "com.fulcrumgenomics",
  organizationName     := "Fulcrum Genomics LLC",
  organizationHomepage := Some(url("http://www.fulcrumgenomics.com")),
  homepage             := Some(url("http://github.com/fulcrumgenomics/sopt")),
  startYear            := Some(2015),
  scalaVersion         := "2.12.2",
  crossScalaVersions   := Seq("2.11.11", "2.12.2"),
  scalacOptions        ++= Seq("-target:jvm-1.8", "-deprecation", "-unchecked"),
  scalacOptions in (Compile, doc) ++= docScalacOptions,
  scalacOptions in (Test, doc) ++= docScalacOptions,
  autoAPIMappings := true,
  testOptions in Test  += Tests.Argument(TestFrameworks.ScalaTest, "-h", Option(System.getenv("TEST_HTML_REPORTS")).getOrElse(htmlReportsDirectory)),
  testOptions in Test  += Tests.Argument("-l", "LongRunningTest"), // ignores long running tests
  // uncomment for full stack traces
  //testOptions in Test  += Tests.Argument("-oD"),
  fork in Test         := true,
  resolvers            += Resolver.jcenterRepo,
  resolvers            += Resolver.sonatypeRepo("public"),
  shellPrompt          := { state => "%s| %s> ".format(GitCommand.prompt.apply(state), version.value) },
  updateOptions        := updateOptions.value.withCachedResolution(true)
) ++ Defaults.coreDefaultSettings

////////////////////////////////////////////////////////////////////////////////////////////////
// sopt project
////////////////////////////////////////////////////////////////////////////////////////////////
lazy val assemblySettings = Seq(
  test in assembly     := {},
  logLevel in assembly := Level.Info
)
lazy val root = Project(id="sopt", base=file("."))
  .settings(commonSettings: _*)
  .settings(unidocSettings: _*)
  .settings(assemblySettings: _*)
  .settings(description := "Scala sopt for Fulcrum Genomics.")
  .settings(
    libraryDependencies ++= Seq(
      "org.scala-lang"       %  "scala-reflect" %  scalaVersion.value,
      "com.fulcrumgenomics"  %% "commons"       % "0.3.0",
      "com.vladsch.flexmark" % "flexmark"       % "0.18.5",

      //---------- Test libraries -------------------//
      "org.scalatest"             %% "scalatest"     % "3.0.1"  % "test->*" excludeAll ExclusionRule(organization="org.junit", name="junit")
    )
  )

////////////////////////////////////////////////////////////////////////////////////////////////
// Merge strategy for assembly
////////////////////////////////////////////////////////////////////////////////////////////////
val customMergeStrategy: String => MergeStrategy = {
  case x if Assembly.isConfigFile(x) =>
    MergeStrategy.concat
  case PathList(ps@_*) if Assembly.isReadme(ps.last) || Assembly.isLicenseFile(ps.last) =>
    MergeStrategy.rename
  case PathList("META-INF", xs@_*) =>
    xs map {
      _.toLowerCase
    } match {
      case ("manifest.mf" :: Nil) | ("index.list" :: Nil) | ("dependencies" :: Nil) =>
        MergeStrategy.discard
      case ps@(x :: xt) if ps.last.endsWith(".sf") || ps.last.endsWith(".dsa") =>
        MergeStrategy.discard
      case "plexus" :: xt =>
        MergeStrategy.discard
      case "spring.tooling" :: xt =>
        MergeStrategy.discard
      case "com.google.guava" :: xt =>
        MergeStrategy.discard
      case "services" :: xt =>
        MergeStrategy.filterDistinctLines
      case ("spring.schemas" :: Nil) | ("spring.handlers" :: Nil) =>
        MergeStrategy.filterDistinctLines
      case _ => MergeStrategy.deduplicate
    }
  case "asm-license.txt" | "overview.html" =>
    MergeStrategy.discard
  case "logback.xml" =>
    MergeStrategy.first
  case _ => MergeStrategy.deduplicate
}
assemblyMergeStrategy in assembly := customMergeStrategy
