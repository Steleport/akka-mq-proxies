name := """akka-amqp-proxies"""

version := "1.4-SNAPSHOT"

organization := "com.github.sstone"

scalaVersion := "2.11.2"

crossScalaVersions  := Seq("2.10.3","2.11.2")


libraryDependencies ++= Seq(
	"com.typesafe.akka" %% "akka-actor" % "2.3.6",
	"org.json4s" %% "json4s-jackson" %  "3.2.10",
	"org.json4s" %% "json4s-ext" %  "3.2.10",
	"com.github.sstone" %% "amqp-client" % "1.4",
	 "ch.qos.logback" % "logback-classic" % "1.0.9",
	 "com.google.protobuf" % "protobuf-java" % "2.4.1",
	 "org.xerial.snappy" % "snappy-java" % "1.0.5.4",
	 "org.apache.thrift" % "libthrift" % "0.9.0",
	 "com.typesafe.akka" %% "akka-testkit" % "2.3.6" % "test",
	 "junit" % "junit" % "4.8.2" % "test",
	 "org.scalatest" %% "scalatest" % "2.2.1"
)

