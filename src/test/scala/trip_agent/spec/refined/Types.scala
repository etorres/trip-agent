package es.eriktorr
package trip_agent.spec.refined

import cats.collections.Range
import cats.{Eq, Show}

object Types:
  opaque type FailureRate <: Double = Double

  object FailureRate:
    private val range = Range(0d, 1d)

    def fromDouble(value: Double): Either[Throwable, FailureRate] =
      Either.cond(
        test = range.contains(value),
        right = value,
        left = IllegalArgumentException(
          s"Failure rate must be between ${range.start} and ${range.end}, but was $value",
        ),
      )

    @SuppressWarnings(Array("org.wartremover.warts.Throw"))
    def applyUnsafe(value: Double): FailureRate =
      fromDouble(value) match
        case Left(error) => throw error
        case Right(valid) => valid

    given Eq[FailureRate] = Eq.fromUniversalEquals

    given Show[FailureRate] = Show.fromToString

    val alwaysSucceed: FailureRate = range.start
    val alwaysFailed: FailureRate = range.end
