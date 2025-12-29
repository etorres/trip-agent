package es.eriktorr
package trip_agent.infrastructure

import cats.effect.IO
import weaver.SimpleIOSuite

object TSIDGenSuite extends SimpleIOSuite:
  test("should generate a random TSID of 8 bytes length"):
    for
      tsid <- TSIDGen[IO].randomTSID
      obtained = tsid.toBytes.length
    yield expect.eql(
      expected = 8,
      found = obtained,
    )
