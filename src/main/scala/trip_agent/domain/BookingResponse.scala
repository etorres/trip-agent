package es.eriktorr
package trip_agent.domain

import cats.derived.*
import cats.{Eq, Show}
import io.circe.Codec

final case class BookingResponse(
    accepted: Boolean,
    bookingId: Option[BookingId],
) derives Codec,
      Eq,
      Show
