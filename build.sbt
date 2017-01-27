name := """akka-mq-proxies"""

version := "2.0.0-SNAPSHOT"

organization := "space.spacelift"

scalaVersion := "2.12.1"

crossScalaVersions  := Seq("2.12.1","2.11.8")

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.4.16",
  "org.json4s" %% "json4s-jackson" %  "3.5.0",
  "org.json4s" %% "json4s-ext" %  "3.5.0",
  "space.spacelift" %% "amqp-scala-client" % "2.0.0-SNAPSHOT",
  "ch.qos.logback" % "logback-classic" % "1.1.2",
  "com.google.protobuf" % "protobuf-java" % "2.4.1",
  "org.xerial.snappy" % "snappy-java" % "1.0.5.4",
  "org.apache.thrift" % "libthrift" % "0.9.0",
  "com.typesafe.akka" %% "akka-testkit" % "2.4.16" % "test",
  "junit" % "junit" % "4.12" % "test",
  "org.scalatest" %% "scalatest" % "3.0.1"
)

