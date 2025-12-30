package es.eriktorr
package trip_agent.application

import io.circe.{Decoder, Encoder}

import java.time.ZonedDateTime

final case class FlightAvailability(
    id: Int,
    from: String,
    to: String,
    departure: ZonedDateTime,
    returnLeg: ZonedDateTime,
    price: Int,
) derives Encoder,
      Decoder
