package es.eriktorr
package trip_agent.application

import trip_agent.application.AvailabilityService.{availabilityFilteredBy, FlightAvailability}
import trip_agent.domain.Flight

import cats.collections.Range
import cats.effect.IO
import org.typelevel.cats.time.instances.zoneddatetime.given

import java.time.LocalDate

trait FlightService:
  def flightsBy(
      departure: LocalDate,
      arrival: LocalDate,
  ): IO[List[Flight]]

object FlightService:
  def impl: FlightService =
    (departure: LocalDate, arrival: LocalDate) =>
      availabilityFilteredBy[
        FlightAvailability,
        Flight,
      ](
        resourcePath = "/flight_availabilities.jsonl",
        range = Range(departure, arrival),
        dateFilter = (availability, range) =>
          range.contains(availability.departure)
            && range.contains(availability.returnLeg),
      )
