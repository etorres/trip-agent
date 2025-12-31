package es.eriktorr
package trip_agent.application

import trip_agent.application.AvailabilityService.{
  availabilityFilteredBy,
  AccommodationAvailability,
}
import trip_agent.domain.Accommodation

import cats.collections.Range
import cats.effect.IO
import org.typelevel.cats.time.instances.zoneddatetime.given

import java.time.LocalDate

trait AccommodationService:
  def accommodationsBy(
      checkin: LocalDate,
      checkout: LocalDate,
  ): IO[List[Accommodation]]

object AccommodationService:
  def impl: AccommodationService =
    (checkin: LocalDate, checkout: LocalDate) =>
      availabilityFilteredBy[
        AccommodationAvailability,
        Accommodation,
      ](
        resourcePath = "/accommodation_availabilities.jsonl",
        range = Range(checkin, checkout),
        dateFilter = (availability, range) =>
          range.contains(availability.availableFrom)
            && range.contains(availability.availableUntil),
      )
