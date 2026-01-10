package es.eriktorr
package trip_agent.domain

import io.circe.testing.{ArbitraryInstances, CodecTests}
import io.hypersistence.tsid.TSID
import org.scalacheck.Arbitrary
import weaver.FunSuite
import weaver.discipline.Discipline

object TSIDCatsSuite extends FunSuite with Discipline:
  import Implicits.given
  val requestIdCodecTests = CodecTests[RequestId]
  checkAll("RequestId", requestIdCodecTests.codec)

  object Implicits extends ArbitraryInstances:
    given Arbitrary[RequestId] =
      Arbitrary:
        val tsid = TSID.Factory.getTsid256
        RequestId(tsid)
