package es.eriktorr
package trip_agent.api

import trip_agent.domain.TripSearchGenerators.questionGen

import io.circe.parser.decode
import io.circe.syntax.EncoderOps
import weaver.SimpleIOSuite
import weaver.scalacheck.Checkers

/** It could be replaced by Circe
  * [[https://circe.github.io/circe/codecs/testing.html Codec Testing]], once it is available for
  * the weaver-test.
  */
object TripSearchRequestSuite extends SimpleIOSuite with Checkers:
  test("should decode JSON that's known to be good"):
    forall(tripSearchRequestGen): request =>
      whenSuccess(decode[TripSearchRequest](rawJsonFrom(request))): obtained =>
        expect.eql(request, obtained)

  test("should produce the expected results"):
    forall(tripSearchRequestGen): request =>
      expect.eql(rawJsonFrom(request), request.asJson.noSpaces)

  private lazy val tripSearchRequestGen =
    questionGen.map(TripSearchRequest.FindTrip.apply)

  private def rawJsonFrom(request: TripSearchRequest) =
    request match
      case TripSearchRequest.FindTrip(question) =>
        val escapedQuestion = question.replace("\n", "\\n")
        s"""{"FindTrip":{"question":"$escapedQuestion"}}"""
