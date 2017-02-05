import Dependencies._

useGpg := true

credentials += Credentials(Path.userHome / ".ivy2" / ".spacelift-credentials")

usePgpKeyHex("561D5885877866DF")

lazy val akkaMqProxies = (project in file("akka-mq-proxies"))
  .settings(Commons.settings:_*)
  .settings(
    scalaVersion := "2.12.1",
    name := "akka-mq-proxies",
    libraryDependencies ++= commonDependencies ++ Seq(
      "org.json4s" %% "json4s-jackson" %  "3.5.0",
      "org.json4s" %% "json4s-ext" %  "3.5.0",
      "org.xerial.snappy" % "snappy-java" % "1.0.5.4",
      "org.apache.thrift" % "libthrift" % "0.9.0"
    )
  )

lazy val akkaMqProxiesAmqp = (project in file("akka-mq-proxies-amqp"))
  .settings(Commons.settings:_*)
  .settings(
    scalaVersion := "2.12.1",
    name := "akka-mq-proxies-amqp",
    libraryDependencies ++= commonDependencies ++ Seq(
      "space.spacelift" %% "amqp-scala-client" % "2.0.0"
    )
  )
  .dependsOn(akkaMqProxies % "compile->compile;test->test")

lazy val akkaMqProxiesZeromq = (project in file("akka-mq-proxies-zeromq"))
  .settings(Commons.settings:_*)
  .settings(
    scalaVersion := "2.12.1",
    name := "akka-mq-proxies-zeromq",
    libraryDependencies ++= commonDependencies
  )
  .dependsOn(akkaMqProxies % "compile->compile;test->test")

lazy val akkaMqProxiesSqs = (project in file("akka-mq-proxies-sqs"))
  .settings(Commons.settings:_*)
  .settings(
    scalaVersion := "2.12.1",
    name := "akka-mq-proxies-sqs",
    libraryDependencies ++= commonDependencies
  )
  .dependsOn(akkaMqProxies % "compile->compile;test->test")
