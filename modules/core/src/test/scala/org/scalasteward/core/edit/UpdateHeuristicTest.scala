package org.scalasteward.core.edit

import org.scalasteward.core.TestSyntax._
import org.scalasteward.core.data.Update.{Group, Single}
import org.scalasteward.core.data.{ArtifactId, GroupId, Update}
import org.scalasteward.core.edit.UpdateHeuristicTest.UpdateOps
import org.scalasteward.core.util.Nel
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class UpdateHeuristicTest extends AnyFunSuite with Matchers {
  test("sbt: build.properties") {
    val original = """sbt.version=1.3.0-RC1"""
    val expected = """sbt.version=1.3.0"""
    Single("org.scala-sbt" % "sbt" % "1.3.0-RC1", Nel.of("1.3.0"))
      .replaceVersionIn(original) shouldBe (Some(expected) -> UpdateHeuristic.original.name)
  }

  test("sbt plugins") {
    val original =
      """addSbtPlugin("pl.project13.scala" % "sbt-jmh" % "0.3.3")
        |addSbtPlugin("org.scala-js" % "sbt-scalajs" % "0.6.24")
        |addSbtPlugin("org.portable-scala" % "sbt-scalajs-crossproject" % "0.4.0")
      """.stripMargin.trim
    val expected =
      """addSbtPlugin("pl.project13.scala" % "sbt-jmh" % "0.3.3")
        |addSbtPlugin("org.scala-js" % "sbt-scalajs" % "0.6.25")
        |addSbtPlugin("org.portable-scala" % "sbt-scalajs-crossproject" % "0.4.0")
      """.stripMargin.trim
    Single("org.scala-js" % "sbt-scalajs" % "0.6.24", Nel.of("0.6.25"))
      .replaceVersionIn(original) shouldBe (Some(expected) -> UpdateHeuristic.moduleId.name)
  }

  test("sbt plugins: missing version") {
    val original =
      """addSbtPlugin("pl.project13.scala" % "sbt-jmh" % "0.3.3")
        |addSbtPlugin("org.scala-js" % "sbt-scalajs" % "0.6.23")
        |addSbtPlugin("org.portable-scala" % "sbt-scalajs-crossproject" % "0.4.0")
      """.stripMargin.trim
    Single("org.scala-js" % "sbt-scalajs" % "0.6.24", Nel.of("0.6.25"))
      .replaceVersionIn(original) shouldBe (None -> UpdateHeuristic.all.last.name)
  }

  test("ignore hyphen in artifactId") {
    val original = """val scalajsJqueryVersion = "0.9.3""""
    val expected = """val scalajsJqueryVersion = "0.9.4""""
    Single("be.doeraene" % "scalajs-jquery" % "0.9.3", Nel.of("0.9.4"))
      .replaceVersionIn(original) shouldBe (Some(expected) -> UpdateHeuristic.original.name)
  }

  test("commented val") {
    val original =
      """// val scalajsJqueryVersion = "0.9.3
        |val scalajsJqueryVersion = "0.9.3" //bla
        |"""".stripMargin.trim
    val expected =
      """// val scalajsJqueryVersion = "0.9.3
        |val scalajsJqueryVersion = "0.9.4" //bla
        |"""".stripMargin.trim
    Single("be.doeraene" % "scalajs-jquery" % "0.9.3", Nel.of("0.9.4"))
      .replaceVersionIn(original) shouldBe (Some(expected) -> UpdateHeuristic.original.name)
  }

  test("commented ModuleIDs") {
    val original =
      """ "be.doeraene" %% "scalajs-jquery"  % "0.9.3"
        | // "be.doeraene" %% "scalajs-jquery"  % "0.9.3"
        |   addSbtPlugin("be.doeraene" %% "scalajs-jquery"  % "0.9.3")
        |   //addSbtPlugin("be.doeraene" %% "scalajs-jquery"  % "0.9.3")
        |"""".stripMargin.trim
    val expected =
      """ "be.doeraene" %% "scalajs-jquery"  % "0.9.4"
        | // "be.doeraene" %% "scalajs-jquery"  % "0.9.3"
        |   addSbtPlugin("be.doeraene" %% "scalajs-jquery"  % "0.9.4")
        |   //addSbtPlugin("be.doeraene" %% "scalajs-jquery"  % "0.9.3")
        |"""".stripMargin.trim
    Single("be.doeraene" % "scalajs-jquery" % "0.9.3", Nel.of("0.9.4"))
      .replaceVersionIn(original) shouldBe (Some(expected) -> UpdateHeuristic.moduleId.name)
  }

  test("ignore '-core' suffix") {
    val original = """val specs2Version = "4.2.0""""
    val expected = """val specs2Version = "4.3.4""""
    Single("org.specs2" % "specs2-core" % "4.2.0", Nel.of("4.3.4"))
      .replaceVersionIn(original) shouldBe (Some(expected) -> UpdateHeuristic.original.name)
  }

  test("use groupId if artifactId is 'core'") {
    val original = """lazy val sttpVersion = "1.3.2""""
    val expected = """lazy val sttpVersion = "1.3.3""""
    Single("com.softwaremill.sttp" % "core" % "1.3.2", Nel.of("1.3.3"))
      .replaceVersionIn(original) shouldBe (Some(expected) -> UpdateHeuristic.original.name)
  }

  test("short groupIds") {
    val original = """|private val mapCommonsDeps = Seq(
                      |    "akka-streams",
                      |    "akka-streams-kafka"
                      |  ).map("com.sky" %% _ % "1.2.0")
                      |""".stripMargin

    val expected = """|private val mapCommonsDeps = Seq(
                      |    "akka-streams",
                      |    "akka-streams-kafka"
                      |  ).map("com.sky" %% _ % "1.3.0")
                      |""".stripMargin

    Group("com.sky" % Nel.of("akka-streams", "akka-streams-kafka") % "1.2.0", Nel.one("1.3.0"))
      .replaceVersionIn(original) shouldBe (Some(expected) -> UpdateHeuristic.completeGroupId.name)
  }

  test("version range") {
    val original = """Seq("org.specs2" %% "specs2-core" % "3.+" % "test")"""
    val expected = """Seq("org.specs2" %% "specs2-core" % "4.3.4" % "test")"""
    Single("org.specs2" % "specs2-core" % "3.+", Nel.of("4.3.4"))
      .replaceVersionIn(original) shouldBe (Some(expected) -> UpdateHeuristic.moduleId.name)
  }

  test("group with prefix val") {
    val original = """ val circe = "0.10.0-M1" """
    val expected = """ val circe = "0.10.0-M2" """
    Group(
      "io.circe" %
        Nel.of("circe-generic", "circe-literal", "circe-parser", "circe-testing") % "0.10.0-M1",
      Nel.of("0.10.0-M2")
    ).replaceVersionIn(original) shouldBe (Some(expected) -> UpdateHeuristic.original.name)
  }

  test("update under different group id") {
    val original = """ "org.spire-math" %% "kind-projector" % "0.9.0""""
    val expected = """ "org.typelevel" %% "kind-projector" % "0.10.0""""
    Single(
      "org.spire-math" % "kind-projector" % "0.9.0",
      Nel.of("0.10.0"),
      Some(GroupId("org.typelevel"))
    ).replaceVersionIn(original) shouldBe (Some(expected) -> UpdateHeuristic.moduleId.name)
  }

  test("group with repeated version") {
    val original =
      """ "com.pepegar" %% "hammock-core"  % "0.8.1",
        | "com.pepegar" %% "hammock-circe" % "0.8.1"
      """.stripMargin.trim
    val expected =
      """ "com.pepegar" %% "hammock-core"  % "0.8.5",
        | "com.pepegar" %% "hammock-circe" % "0.8.5"
      """.stripMargin.trim
    Group("com.pepegar" % Nel.of("hammock-core", "hammock-circe") % "0.8.1", Nel.of("0.8.5"))
      .replaceVersionIn(original) shouldBe (Some(expected) -> UpdateHeuristic.moduleId.name)
  }

  test("same version, same artifact prefix, different groupId") {
    val original =
      """ "org.http4s" %% "jawn-fs2" % "0.14.0"
        | "org.typelevel" %% "jawn-json4s"  % "0.14.0",
        | "org.typelevel" %% "jawn-play" % "0.14.0"
      """.stripMargin.trim
    val expected =
      """ "org.http4s" %% "jawn-fs2" % "0.14.0"
        | "org.typelevel" %% "jawn-json4s"  % "0.14.1",
        | "org.typelevel" %% "jawn-play" % "0.14.1"
      """.stripMargin.trim
    Group("org.typelevel" % Nel.of("jawn-json4s", "jawn-play") % "0.14.0", Nel.of("0.14.1"))
      .replaceVersionIn(original) shouldBe (Some(expected) -> UpdateHeuristic.moduleId.name)
  }

  test("artifactIds are common suffixes") {
    val original =
      """lazy val scalajsReactVersion = "1.2.3"
        |lazy val logbackVersion = "1.2.3"
      """.stripMargin
    val expected =
      """lazy val scalajsReactVersion = "1.3.1"
        |lazy val logbackVersion = "1.2.3"
      """.stripMargin
    Group("com.github.japgolly.scalajs-react" % Nel.of("core", "extra") % "1.2.3", Nel.of("1.3.1"))
      .replaceVersionIn(original) shouldBe (Some(expected) -> UpdateHeuristic.original.name)
  }

  test("ignore 'previous' prefix") {
    val original =
      """val circeVersion = "0.10.0"
        |val previousCirceIterateeVersion = "0.10.0"
      """.stripMargin
    val expected =
      """val circeVersion = "0.10.1"
        |val previousCirceIterateeVersion = "0.10.0"
      """.stripMargin
    Group("io.circe" % Nel.of("circe-jawn", "circe-testing") % "0.10.0", Nel.of("0.10.1"))
      .replaceVersionIn(original) shouldBe (Some(expected) -> UpdateHeuristic.original.name)
  }

  test("ignore mimaPreviousArtifacts") {
    val original =
      """"io.dropwizard.metrics" % "metrics-core" % "4.0.1"
        |mimaPreviousArtifacts := Set("io.dropwizard.metrics" %% "metrics-core" % "4.0.1")
      """.stripMargin
    val expected =
      """"io.dropwizard.metrics" % "metrics-core" % "4.0.3"
        |mimaPreviousArtifacts := Set("io.dropwizard.metrics" %% "metrics-core" % "4.0.1")
      """.stripMargin
    Group(
      "io.dropwizard.metrics" % Nel.of("metrics-core", "metrics-healthchecks") % "4.0.1",
      Nel.of("4.0.3")
    ).replaceVersionIn(original) shouldBe (Some(expected) -> UpdateHeuristic.moduleId.name)
  }

  test("artifactId with dot") {
    val original = """ def plotlyJs = "1.41.3" """
    val expected = """ def plotlyJs = "1.43.2" """
    Single("org.webjars.bower" % "plotly.js" % "1.41.3", Nel.of("1.43.2"))
      .replaceVersionIn(original) shouldBe (Some(expected) -> UpdateHeuristic.original.name)
  }

  test("val with backticks") {
    val original = """ val `plotly.js` = "1.41.3" """
    val expected = """ val `plotly.js` = "1.43.2" """
    Single("org.webjars.bower" % "plotly.js" % "1.41.3", Nel.of("1.43.2"))
      .replaceVersionIn(original) shouldBe (Some(expected) -> UpdateHeuristic.original.name)
  }

  test("word from artifactId") {
    val original = """lazy val circeVersion = "0.9.3""""
    val expected = """lazy val circeVersion = "0.11.1""""
    Single(
      "io.circe" % ArtifactId("circe-generic", "circe-generic_2.12") % "0.9.3",
      Nel.of("0.11.1")
    ).replaceVersionIn(original) shouldBe (Some(expected) -> UpdateHeuristic.relaxed.name)
  }

  test("artifactId with underscore") {
    val original = """val scShapelessV = "1.1.6""""
    val expected = """val scShapelessV = "1.1.8""""
    Single("com.github.alexarchambault" % "scalacheck-shapeless_1.13" % "1.1.6", Nel.of("1.1.8"))
      .replaceVersionIn(original) shouldBe (Some(expected) -> UpdateHeuristic.relaxed.name)
  }

  test("camel case artifactId") {
    val original = """val hikariVersion = "3.3.0" """
    val expected = """val hikariVersion = "3.4.0" """
    Single("com.zaxxer" % "HikariCP" % "3.3.0", Nel.of("3.4.0"))
      .replaceVersionIn(original) shouldBe (Some(expected) -> UpdateHeuristic.relaxed.name)
  }

  test("mongo from mongodb") {
    val original = """val mongoVersion = "3.7.0" """
    val expected = """val mongoVersion = "3.7.1" """
    Group(
      "org.mongodb" %
        Nel.of("mongodb-driver", "mongodb-driver-async", "mongodb-driver-core") % "3.7.0",
      Nel.of("3.7.1")
    ).replaceVersionIn(original) shouldBe (Some(expected) -> UpdateHeuristic.sliding.name)
  }

  test("artifactId with common suffix") {
    val original = """case _ => "1.0.2" """
    Single("co.fs2" % "fs2-core" % "1.0.2", Nel.of("1.0.4"))
      .replaceVersionIn(original) shouldBe (None -> UpdateHeuristic.all.last.name)
  }

  test("word from groupId") {
    val original = """val acolyteVersion = "1.0.49" """
    val expected = """val acolyteVersion = "1.0.51" """
    Single("org.eu.acolyte" % "jdbc-driver" % "1.0.49", Nel.of("1.0.51"))
      .replaceVersionIn(original) shouldBe (Some(expected) -> UpdateHeuristic.groupId.name)
  }

  test("specific to scalafmt: should be Scala version agnostic") {
    Seq(
      ("""version = "2.0.0" """, """version = "2.0.1" """),
      ("""version="2.0.0"""", """version="2.0.1""""),
      ("""version = 2.0.0 """, """version = 2.0.1 """),
      ("""version=2.0.0 """, """version=2.0.1 """)
    ).foreach {
      case (original, expected) =>
        Single(
          "org.scalameta" % ArtifactId("scalafmt-core", "scalafmt-core_2.12") % "2.0.0",
          Nel.of("2.0.1")
        ).replaceVersionIn(original) shouldBe (Some(expected) -> UpdateHeuristic.specific.name)
    }

    val original = """version=2.0.0"""
    Single("org.scalameta" % "other-artifact" % "2.0.0", Nel.of("2.0.1"))
      .replaceVersionIn(original) shouldBe (None -> UpdateHeuristic.all.last.name)
  }

  test("ignore TLD") {
    val original = """ "com.propensive" %% "contextual" % "1.0.1" """
    Single("com.slamdata" % "fs2-gzip" % "1.0.1", Nel.of("1.1.1"))
      .replaceVersionIn(original) shouldBe (None -> UpdateHeuristic.all.last.name)
  }

  test("ignore short words") {
    val original = "SBT_VERSION=1.2.7"
    Single("org.scala-sbt" % "scripted-plugin" % "1.2.7", Nel.of("1.2.8"))
      .replaceVersionIn(original) shouldBe (None -> UpdateHeuristic.all.last.name)
  }

  test("ignore 'scala' substring") {
    val original = """ val scalaTestVersion = "3.0.7" """
    Single("org.scalactic" % "scalactic" % "3.0.7", Nel.of("3.0.8"))
      .replaceVersionIn(original) shouldBe (None -> UpdateHeuristic.all.last.name)
  }

  test("version that contains the current version as proper substring") {
    val original =
      """
      libraryDependencies += "com.thoughtworks.dsl" %%% "keywords-using" % "1.2.0" % Optional
      libraryDependencies += "com.thoughtworks.dsl" %%% "keywords-each"  % "1.2.0+14-7a373cbd" % Optional
      """
    val expected =
      """
      libraryDependencies += "com.thoughtworks.dsl" %%% "keywords-using" % "1.3.0" % Optional
      libraryDependencies += "com.thoughtworks.dsl" %%% "keywords-each"  % "1.2.0+14-7a373cbd" % Optional
      """
    Group(
      "com.thoughtworks.dsl" % Nel.of("keywords-each", "keywords-using") % "1.2.0",
      Nel.of("1.3.0")
    ).replaceVersionIn(original) shouldBe (Some(expected) -> UpdateHeuristic.moduleId.name)
  }

  test("prevent exception: named capturing group is missing trailing '}'") {
    val original = """ "org.nd4j" % s"nd4j-""" + "$" + """{nd4jRuntime.value}-platform" % "0.8.0""""
    val expected = """ "org.nd4j" % s"nd4j-""" + "$" + """{nd4jRuntime.value}-platform" % "0.9.1""""
    Group("org.nd4j" % Nel.of("nd4j-api", "nd4j-native-platform") % "0.8.0", Nel.of("0.9.1"))
      .replaceVersionIn(original) shouldBe (Some(expected) -> UpdateHeuristic.strict.name)
  }

  test("NOK: change of unrelated ModuleID") {
    val original = """ "com.geirsson" % "sbt-ci-release" % "1.2.1" """
    val expected = """ "com.geirsson" % "sbt-ci-release" % "1.2.4" """
    Group(
      "org.scala-sbt" % Nel.of("sbt-launch", "scripted-plugin", "scripted-sbt") % "1.2.1",
      Nel.of("1.2.4")
    ).replaceVersionIn(original) shouldBe (Some(expected) -> UpdateHeuristic.relaxed.name)
  }

  test("disable updates on single lines with `off` (no `on`)") {
    val original =
      """  "com.typesafe.akka" %% "akka-actor" % "2.4.0", // scala-steward:off
        |  "com.typesafe.akka" %% "akka-testkit" % "2.4.0",
        |  """.stripMargin.trim
    val expected =
      """  "com.typesafe.akka" %% "akka-actor" % "2.4.0", // scala-steward:off
        |  "com.typesafe.akka" %% "akka-testkit" % "2.5.0",
        |  """.stripMargin.trim
    Group("com.typesafe.akka" % Nel.of("akka-actor", "akka-testkit") % "2.4.0", Nel.of("2.5.0"))
      .replaceVersionIn(original) shouldBe (Some(expected) -> UpdateHeuristic.moduleId.name)
  }

  test("disable updates on multiple lines after `off` (no `on`)") {
    val original =
      """  // scala-steward:off
        |  "com.typesafe.akka" %% "akka-actor" % "2.4.0",
        |  "com.typesafe.akka" %% "akka-testkit" % "2.4.0",
        |  """.stripMargin.trim
    Group("com.typesafe.akka" % Nel.of("akka-actor", "akka-testkit") % "2.4.0", Nel.of("2.5.0"))
      .replaceVersionIn(original) shouldBe (None -> UpdateHeuristic.all.last.name)
  }

  test("update multiple lines between `on` and `off`") {
    val original =
      """  // scala-steward:off
        |  "com.typesafe.akka" %% "akka-actor" % "2.4.20",
        |  // scala-steward:on
        |  "com.typesafe.akka" %% "akka-slf4j" % "2.4.20" % "test"
        |  // scala-steward:off
        |  "com.typesafe.akka" %% "akka-testkit" % "2.4.20" % "test"
        |  """.stripMargin.trim
    val expected =
      """  // scala-steward:off
        |  "com.typesafe.akka" %% "akka-actor" % "2.4.20",
        |  // scala-steward:on
        |  "com.typesafe.akka" %% "akka-slf4j" % "2.5.0" % "test"
        |  // scala-steward:off
        |  "com.typesafe.akka" %% "akka-testkit" % "2.4.20" % "test"
        |  """.stripMargin.trim
    Group(
      "com.typesafe.akka" % Nel.of("akka-actor", "akka-testkit", "akka-slf4j") % "2.4.20",
      Nel.of("2.5.0")
    ).replaceVersionIn(original) shouldBe (Some(expected) -> UpdateHeuristic.moduleId.name)
  }

  test("similar artifactIds and same version") {
    val original =
      """ "org.typelevel" %%% "cats-core" % "2.0.0-M4",
        | "org.typelevel" %%% "cats-laws" % "2.0.0-M4" % "test",
        | "org.typelevel" %%% "cats-effect" % "2.0.0-M4",
        | "org.typelevel" %%% "cats-effect-laws" % "2.0.0-M4" % "test",
        |""".stripMargin
    val expected =
      """ "org.typelevel" %%% "cats-core" % "2.0.0-RC1",
        | "org.typelevel" %%% "cats-laws" % "2.0.0-RC1" % "test",
        | "org.typelevel" %%% "cats-effect" % "2.0.0-M4",
        | "org.typelevel" %%% "cats-effect-laws" % "2.0.0-M4" % "test",
        |""".stripMargin
    Group("org.typelevel" % Nel.of("cats-core", "cats-laws") % "2.0.0-M4", Nel.of("2.0.0-RC1"))
      .replaceVersionIn(original) shouldBe (Some(expected) -> UpdateHeuristic.moduleId.name)
  }

  test("ammonite script syntax") {
    val original = " import $ivy.`org.typelevel::cats-core:1.2.0` ".stripMargin
    val expected = " import $ivy.`org.typelevel::cats-core:1.3.0` ".stripMargin

    Single("org.typelevel" % "cats-core" % "1.2.0", Nel.of("1.3.0"))
      .replaceVersionIn(original) shouldBe (Some(expected) -> UpdateHeuristic.moduleId.name)
  }
}

object UpdateHeuristicTest {
  implicit class UpdateOps(update: Update) {
    def replaceVersionIn(target: String): (Option[String], String) =
      UpdateHeuristic.all.foldLeft((Option.empty[String], "")) {
        case ((None, _), heuristic) => (heuristic.replaceVersion(update)(target), heuristic.name)
        case (result, _)            => result
      }
  }
}
