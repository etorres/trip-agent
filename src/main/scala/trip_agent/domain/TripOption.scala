package es.eriktorr
package trip_agent.domain

import cats.derived.*
import cats.{Eq, Show}
import io.circe.Codec

final case class TripOption(
    flightId: Flight.Id,
    accommodationId: Accommodation.Id,
) derives Codec,
      Eq,
      Show
