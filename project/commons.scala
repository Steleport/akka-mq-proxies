import sbt._
import Keys._
import sbtprotobuf.{ProtobufPlugin=>PB}

object Commons {
  val appVersion = "2.0.0-SNAPSHOT"

  val appOrganization = "space.spacelift"

  val settings: Seq[Def.Setting[_]] = Seq(
    version := appVersion,
    organization := appOrganization,
    scalaVersion := "2.12.1",
    scalacOptions  ++= Seq("-feature", "-language:postfixOps", "-unchecked", "-deprecation"),
    resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/",
    publishMavenStyle := true,
    publishTo := {
      val nexus = "https://oss.sonatype.org/"
      if (isSnapshot.value)
        Some("snapshots" at nexus + "content/repositories/snapshots")
      else
        Some("releases"  at nexus + "service/local/staging/deploy/maven2")
    },
    publishArtifact in Test := false,
    pomIncludeRepository := { _ => false },
    pomExtra := (
      <url>http://spacelift.space</url>
      <licenses>
        <license>
          <name>MIT License</name>
          <url>http://www.opensource.org/licenses/mit-license.php</url>
          <distribution>repo</distribution>
        </license>
      </licenses>
      <scm>
        <url>https://github.com/spacelift/akka-mq-proxies</url>
        <connection>scm:git:git@github.com:spacelift/akka-mq-proxies.git</connection>
      </scm>
      <developers>
        <developer>
          <id>drheart</id>
          <name>Dustin R. Heart</name>
          <url>http://spacelift.space</url>
        </developer>
      </developers>)
  ) ++ PB.protobufSettings ++ Seq(
    sourceDirectory in PB.protobufConfig := (resourceDirectory in Test).value,
    javaSource in PB.protobufConfig := ((sourceDirectory in Test).value / "java-protobuf"),
    version in PB.protobufConfig := "2.4.1"
  )
}
