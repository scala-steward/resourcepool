
name := "resourcepool"

organization := "com.wellfactored"
scalaVersion := "2.12.6"
version      := "0.1.0-SNAPSHOT"

crossScalaVersions := Seq("2.12.6", "2.11.12")

libraryDependencies ++= Seq(
  "co.fs2" %% "fs2-core" % "1.0.0-M2",
  "org.scalatest" %% "scalatest" % "3.0.5" % Test
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

