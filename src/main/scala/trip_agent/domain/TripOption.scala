package es.eriktorr
package trip_agent.domain

import cats.derived.*
import cats.{Eq, Show}
import io.circe.Codec

final case class TripOption(
    flightId: Int,
    accommodationId: Int,
) derives Codec,
      Eq,
      Show
