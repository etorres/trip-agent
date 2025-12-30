package es.eriktorr
package trip_agent.application

import io.circe.{Decoder, Encoder}

import java.time.ZonedDateTime

final case class AccommodationAvailability(
    id: Int,
    name: String,
    neighborhood: String,
    availableFrom: ZonedDateTime,
    availableUntil: ZonedDateTime,
    pricePerNight: Int,
) derives Encoder,
      Decoder
