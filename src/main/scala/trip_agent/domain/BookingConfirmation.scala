package es.eriktorr
package trip_agent.domain

import cats.derived.*
import cats.{Eq, Show}
import io.circe.Codec

final case class BookingConfirmation(
    accepted: Boolean,
    bookingId: Option[BookingId],
) derives Codec,
      Eq,
      Show

object BookingConfirmation:
  lazy val notBooked: BookingConfirmation =
    BookingConfirmation(
      accepted = false,
      bookingId = None,
    )
