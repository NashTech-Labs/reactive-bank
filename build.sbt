name := "accounts"

organization := "reactive-bank"

version := "0.1-SNAPSHOT"

scalaVersion := "2.13.3"

val akkaHttpVersion = "10.2.1"
val akkaVersion    = "2.6.10"
val akkaManagementVersion =  "1.0.8"
val slf4jVersion = "1.7.30"
val logbackVersion = "1.2.3"
val scalaTestVersion = "3.2.2"
val typesafeConfigVersion = "1.4.0"
val hibernateVersion = "5.4.22.Final"

fork := true
parallelExecution in ThisBuild := false

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-http"            % akkaHttpVersion,
  "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpVersion,
  "com.typesafe.akka" %% "akka-cluster"         % akkaVersion,
  "com.typesafe.akka" %% "akka-cluster-sharding"% akkaVersion,
  "com.typesafe.akka" %% "akka-serialization-jackson" % akkaVersion,
  "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
  "com.lightbend.akka.management" %% "akka-management" % akkaManagementVersion,
  "com.lightbend.akka.management" %% "akka-management-cluster-http" % akkaManagementVersion,

  "com.h2database" % "h2" % "1.4.200",
  "org.hibernate" % "hibernate-entitymanager" % hibernateVersion,
  "org.hibernate" % "hibernate-c3p0" % hibernateVersion,

  //Logback
  "ch.qos.logback" % "logback-classic" % logbackVersion,

  //Test dependencies
  "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpVersion % Test,
  "com.typesafe.akka" %% "akka-testkit" % akkaVersion % Test,
  "org.scalatest" %% "scalatest" % scalaTestVersion% Test,
)

dependencyOverrides ++= Seq(
  "org.slf4j" % "slf4j-api" % slf4jVersion,
  "com.typesafe" % "config" % typesafeConfigVersion,
  "com.typesafe.akka" %% "akka-actor"% akkaVersion,
  "com.typesafe.akka" %% "akka-cluster"         % akkaVersion,
  "com.typesafe.akka" %% "akka-cluster-sharding"% akkaVersion,
  "com.typesafe.akka" %% "akka-coordination"% akkaVersion,
  "com.typesafe.akka" %% "akka-stream"% akkaVersion,
  "com.typesafe.akka" %% "akka-http"            % akkaHttpVersion,
  "com.typesafe.akka" %% "akka-http-core"            % akkaHttpVersion,
  "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpVersion,
)
