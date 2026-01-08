package es.eriktorr
package trip_agent.application

import trip_agent.domain.{BookingConfirmation, BookingId}
import trip_agent.domain.TSIDCats.given
import trip_agent.infrastructure.data.error.HandledError

import cats.effect.{IO, Ref}
import cats.implicits.{catsSyntaxEq, showInterpolator}

trait BookingService:
  def book(confirmation: BookingConfirmation): IO[Unit]

object BookingService:
  def impl(
      stateRef: Ref[IO, BookingServiceState],
  ): BookingService =
    (confirmation: BookingConfirmation) =>
      stateRef.flatModify: currentState =>
        val (effect, update) =
          currentState.bookedTrips.find(_ === confirmation.bookingId) match
            case Some(value) =>
              val effect = IO.raiseError[Unit](
                BookingServiceError.DuplicatedBookingId(value),
              )
              val update = currentState.bookedTrips
              effect -> update
            case None =>
              val effect = IO.println(show"""Booking trip with options:
                                            |${confirmation.tripOption}
                                            |""".stripMargin)
              val update = confirmation.bookingId :: currentState.bookedTrips
              effect -> update
        (currentState.copy(update), effect)

  final case class BookingServiceState(
      bookedTrips: List[BookingId],
  )

  object BookingServiceState:
    val empty: BookingServiceState =
      BookingServiceState(List.empty)

  sealed abstract class BookingServiceError(message: String) extends HandledError(message)

  object BookingServiceError:
    final case class DuplicatedBookingId(bookingId: BookingId)
        extends BookingServiceError(show"Trip with ID ${bookingId.value} has already been booked")
