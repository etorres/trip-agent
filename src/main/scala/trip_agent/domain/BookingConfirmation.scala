package es.eriktorr
package trip_agent.domain

import cats.derived.*
import cats.{Eq, Show}
import io.circe.Codec

final case class BookingConfirmation(
    bookingId: BookingId,
    tripOption: TripOption,
) derives Codec,
      Eq,
      Show
