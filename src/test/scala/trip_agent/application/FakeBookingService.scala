package es.eriktorr
package trip_agent.application

import trip_agent.application.FakeBookingService.BookingServiceState
import trip_agent.domain.TripSelection
import trip_agent.spec.SimulatedFailure
import trip_agent.spec.refined.Types.FailureRate

import cats.effect.std.Random
import cats.effect.{IO, Ref}
import cats.mtl.Raise

final class FakeBookingService(
    stateRef: Ref[IO, BookingServiceState],
    failureRate: FailureRate = FailureRate.alwaysSucceed,
)(using random: Random[IO])
    extends BookingService:
  override def book(
      selection: TripSelection,
  )(using Raise[IO, BookingService.Error]): IO[Unit] =
    random.nextDouble.flatMap: roll =>
      if roll < failureRate
      then IO.raiseError(SimulatedFailure)
      else
        stateRef.update: currentState =>
          currentState.copy(
            bookings = selection :: currentState.bookings,
          )

object FakeBookingService:
  final case class BookingServiceState(
      bookings: List[TripSelection],
  )

  object BookingServiceState:
    val empty: BookingServiceState =
      BookingServiceState(List.empty)
