package es.eriktorr
package trip_agent.domain

import io.circe.parser.decode
import io.circe.syntax.EncoderOps
import io.hypersistence.tsid.TSID
import weaver.SimpleIOSuite
import weaver.scalacheck.Checkers

/** It could be replaced by Circe
  * [[https://circe.github.io/circe/codecs/testing.html Codec Testing]], once it is available for
  * the weaver-test.
  */
object TSIDCatsSuite extends SimpleIOSuite with Checkers:
  test("should decode JSON that's known to be good"):
    forall(requestIdGen): requestId =>
      whenSuccess(decode[RequestId](rawJsonFrom(requestId))): obtained =>
        expect.eql(requestId, obtained)

  test("should produce the expected results"):
    forall(requestIdGen): requestId =>
      expect.eql(rawJsonFrom(requestId), requestId.asJson.noSpaces)

  private lazy val requestIdGen =
    val tsid = TSID.Factory.getTsid256
    RequestId(tsid)

  private def rawJsonFrom(requestId: RequestId) =
    s"""{"value":"${requestId.value.toString}"}"""
