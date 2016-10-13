name := """akka-amqp-proxies"""

version := "1.5-SNAPSHOT"

organization := "com.github.sstone"

scalaVersion := "2.11.8"

crossScalaVersions  := Seq("2.10.3","2.11.8")


libraryDependencies ++= Seq(
	"com.typesafe.akka" %% "akka-actor" % "2.4.3",
	"org.json4s" %% "json4s-jackson" %  "3.2.10",
	"org.json4s" %% "json4s-ext" %  "3.2.10",
	"com.github.sstone" %% "amqp-client" % "1.6-SNAPSHOT",
	 "ch.qos.logback" % "logback-classic" % "1.1.2",
	 "com.google.protobuf" % "protobuf-java" % "2.4.1",
	 "org.xerial.snappy" % "snappy-java" % "1.0.5.4",
	 "org.apache.thrift" % "libthrift" % "0.9.0",
	 "com.typesafe.akka" %% "akka-testkit" % "2.4.3" % "test",
	 "junit" % "junit" % "4.12" % "test",
	 "org.scalatest" %% "scalatest" % "2.2.5"
)

