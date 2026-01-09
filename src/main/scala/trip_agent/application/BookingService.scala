package es.eriktorr
package trip_agent.application

import trip_agent.domain.TSIDCats.given
import trip_agent.domain.{BookingId, TripSelection}
import trip_agent.infrastructure.data.error.HandledError

import cats.effect.{IO, Ref}
import cats.implicits.{catsSyntaxEq, showInterpolator}
import cats.mtl.Raise
import cats.mtl.implicits.given
import org.typelevel.log4cats.StructuredLogger

trait BookingService:
  def book(
      selection: TripSelection,
  )(using Raise[IO, BookingService.Error]): IO[Unit]

object BookingService:
  def impl(
      stateRef: Ref[IO, BookingService.State],
  )(using logger: StructuredLogger[IO]): BookingService =
    new BookingService:
      override def book(
          selection: TripSelection,
      )(using Raise[IO, Error]): IO[Unit] =
        stateRef.flatModify: currentState =>
          val (effect, update) =
            currentState.bookedTrips.find(_ === selection.bookingId) match
              case Some(value) =>
                val effect = BookingService.Error
                  .DuplicatedBookingId(value)
                  .raise[IO, Unit]
                val update = currentState.bookedTrips
                effect -> update
              case None =>
                val effect = logger.info(show"""Booking trip with options:
                                               |${selection.tripOption}
                                               |""".stripMargin)
                val update = selection.bookingId :: currentState.bookedTrips
                effect -> update
          (currentState.copy(update), effect)

  final case class State(
      bookedTrips: List[BookingId],
  )

  object State:
    val empty: State = State(List.empty)

  enum Error(val message: String) extends HandledError(message):
    case DuplicatedBookingId(bookingId: BookingId)
        extends Error(show"Trip with ID ${bookingId.value} has already been booked")
