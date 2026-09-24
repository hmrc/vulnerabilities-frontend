import uk.gov.hmrc.DefaultBuildSettings
import scoverage.ScoverageKeys
import uk.gov.hmrc.versioning.SbtGitVersioning.autoImport.majorVersion

lazy val appName: String = "vulnerabilities-frontend"

ThisBuild / majorVersion := 0
ThisBuild / scalaVersion := "3.3.5"

lazy val microservice = (project in file("."))
  .enablePlugins(PlayScala, SbtDistributablesPlugin)
  .disablePlugins(JUnitXmlReportPlugin) //Required to prevent https://github.com/scalatest/scalatest/issues/1427
  .settings(Test / fork := true)
  .settings(ThisBuild / useSuperShell := false)
  .settings(
    name := appName,
    PlayKeys.playDefaultPort := 8865,
    ScoverageKeys.coverageExcludedFiles := "<empty>;Reverse.*;.*Routes.*;.*views.html.*;",
    ScoverageKeys.coverageMinimumStmtTotal := 78,
    ScoverageKeys.coverageFailOnMinimum := true,
    ScoverageKeys.coverageHighlighting := true,
    scalacOptions ++= Seq(
      "-feature",
      "-Wconf:cat=deprecation:w,cat=feature:w,src=target/.*:s"
    ),
    libraryDependencies ++= AppDependencies(),
    retrieveManaged := true,
    pipelineStages := Seq(digest)
  )

lazy val it = project
  .enablePlugins(PlayScala)
  .disablePlugins(JUnitXmlReportPlugin)
  .dependsOn(microservice % "test->compile")
  .settings(DefaultBuildSettings.itSettings())
  .settings(
    libraryDependencies ++= AppDependencies.test,
    Test / fork := true,
    Test / parallelExecution := false
  )

addCommandAlias("check", ";test;it/test")
