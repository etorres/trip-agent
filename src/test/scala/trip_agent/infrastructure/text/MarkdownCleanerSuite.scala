package es.eriktorr
package trip_agent.infrastructure.text

import trip_agent.domain.TripSearchGenerators.{accommodationGen, flightGen}
import trip_agent.infrastructure.text.MarkdownCleaner
import trip_agent.spec.StringGenerators.alphaNumericStringBetween

import io.circe.syntax.EncoderOps
import org.scalacheck.Gen
import weaver.SimpleIOSuite
import weaver.scalacheck.Checkers

object MarkdownCleanerSuite extends SimpleIOSuite with Checkers:
  test("Should strip code fences from text"):
    forall(testCaseGen): (expected, raw) =>
      val obtained = MarkdownCleaner.stripCodeFences(raw)
      expect.eql(expected, obtained)

  private lazy val testCaseGen =
    for
      text <- Gen.frequency(
        1 -> accommodationGen().map(_.asJson.spaces4),
        1 -> flightGen().map(_.asJson.spaces4),
        1 -> alphaNumericStringBetween(3, 12),
      )
      raw <- Gen.frequency(
        4 -> alphaNumericStringBetween(3, 12).map: lang =>
          s"""```$lang
             |$text
             |```""".stripMargin,
        2 ->
          s"""```
             |$text
             |```""".stripMargin,
        1 -> text,
      )
    yield (text, raw)
