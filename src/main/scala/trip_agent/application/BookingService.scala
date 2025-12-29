package es.eriktorr
package trip_agent.application

import trip_agent.domain.{Accommodation, Flight}

import cats.effect.IO
import cats.implicits.showInterpolator

trait BookingService:
  def book(
      emailAddress: String,
      accommodations: List[Accommodation],
      flights: List[Flight],
  ): IO[Unit]

object BookingService:
  def impl: BookingService =
    (
        emailAddress: String,
        accommodations: List[Accommodation],
        flights: List[Flight],
    ) =>
      IO.println(
        show"Booking trip with emailAddress: $emailAddress, accommodations: $accommodations, and flights: $flights",
      ) // TODO
