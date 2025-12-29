package es.eriktorr
package trip_agent.application

import trip_agent.application.FakeBookingService.BookingServiceState
import trip_agent.domain.{Accommodation, Flight}

import cats.effect.{IO, Ref}

final class FakeBookingService(
    stateRef: Ref[IO, BookingServiceState],
) extends BookingService:
  override def book(
      emailAddress: String,
      accommodations: List[Accommodation],
      flights: List[Flight],
  ): IO[Unit] =
    stateRef.update: currentState =>
      currentState.copy(
        bookings = (emailAddress, accommodations, flights) :: currentState.bookings,
      )

object FakeBookingService:
  final case class BookingServiceState(
      bookings: List[(String, List[Accommodation], List[Flight])],
  )

  object BookingServiceState:
    val empty: BookingServiceState =
      BookingServiceState(List.empty)
