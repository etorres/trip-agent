package es.eriktorr
package trip_agent.application

import trip_agent.application.FakeBookingService.BookingServiceState
import trip_agent.domain.TripSelection

import cats.effect.{IO, Ref}

final class FakeBookingService(
    stateRef: Ref[IO, BookingServiceState],
) extends BookingService:
  override def book(
      selection: TripSelection,
  ): IO[Unit] =
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
