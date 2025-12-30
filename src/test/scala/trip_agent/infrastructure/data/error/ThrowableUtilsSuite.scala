package es.eriktorr
package trip_agent.infrastructure.data.error

import trip_agent.spec.StringGenerators.alphaNumericStringBetween

import cats.Show
import org.scalacheck.Gen
import weaver.SimpleIOSuite
import weaver.scalacheck.Checkers

import java.io.IOException

object ThrowableUtilsSuite extends SimpleIOSuite with Checkers:
  test("Should walk an error chain until the root cause"):
    forall(testCaseGen): (rootCause, nestedError) =>
      val obtained = ThrowableUtils.rootCause(nestedError)
      expect.same(rootCause, obtained)

  private given Show[Exception] = Show.fromToString

  private lazy val testCaseGen =
    for
      rootCause <- errorGen(None)
      nestedErrorLevel1 <- errorGen(Some(rootCause))
      nestedErrorLevel2 <- errorGen(Some(nestedErrorLevel1))
      nestedErrorLevel3 <- errorGen(Some(nestedErrorLevel2))
      nestedErrorLevel4 <- errorGen(Some(nestedErrorLevel3))
    yield rootCause -> nestedErrorLevel4

  @SuppressWarnings(Array("org.wartremover.warts.Null"))
  private def errorGen(causeGen: Gen[Option[Throwable]]) =
    import scala.language.unsafeNulls
    for
      message <- alphaNumericStringBetween(3, 12)
      maybeCause <- causeGen
      error <- Gen.frequency(
        1 -> IllegalArgumentException(message, maybeCause.orNull),
        1 -> IllegalStateException(message, maybeCause.orNull),
        1 -> RuntimeException(message, maybeCause.orNull),
        1 -> IOException(message, maybeCause.orNull),
      )
    yield error
