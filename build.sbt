
name := "resourcepool"

organization := "com.wellfactored"
scalaVersion := "2.12.6"
version := "1.1.0"
startYear := Some(2018)
organizationName := "Well-Factored Software Ltd."
licenses += ("Apache-2.0", new URL("https://www.apache.org/licenses/LICENSE-2.0.txt"))

enablePlugins(AutomateHeaderPlugin)

libraryDependencies ++= Seq(
  "co.fs2" %% "fs2-core" % "2.2.0",

  "org.scalatest" %% "scalatest" % "3.1.0" % Test
)

scalacOptions ++= Seq(
  "-deprecation",
  "-encoding", "UTF-8",
  "-language:higherKinds",
  "-language:postfixOps",
  "-feature",
  "-unchecked",
  "-Xfuture",
  "-Xlint",
  "-Yno-adapted-args",
  "-Ypartial-unification",
  "-Ywarn-numeric-widen",
  "-Ywarn-value-discard",
  "-Xfatal-warnings"
)

