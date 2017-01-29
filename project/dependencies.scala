import sbt._
import Keys._

object Dependencies {
  val akkaVersion = "2.4.16"
  
  val akkaDependencies: Seq[ModuleID] = Seq(
    "com.typesafe.akka" %% "akka-actor" % akkaVersion
  )

  val testDependencies: Seq[ModuleID] = Seq(
    "com.typesafe.akka" %% "akka-testkit" % akkaVersion % "test",
    "junit" % "junit" % "4.12" % "test",
    "org.scalatest" %% "scalatest" % "3.0.1" % "test"
  )
  
  val commonDependencies: Seq[ModuleID] = akkaDependencies ++ testDependencies ++ Seq(
    "ch.qos.logback" % "logback-classic" % "1.1.2",
    "javax.inject" % "javax.inject" % "1"
  )
}
