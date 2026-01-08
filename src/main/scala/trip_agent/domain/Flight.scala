package es.eriktorr
package trip_agent.domain

import cats.derived.*
import cats.{Eq, Show}
import io.circe.{Codec, Decoder, Encoder}
import org.typelevel.cats.time.instances.zoneddatetime.given

import java.time.ZonedDateTime

final case class Flight(
    id: Int,
    from: String,
    to: String,
    departure: ZonedDateTime,
    arrival: ZonedDateTime,
    price: Int,
) derives Codec,
      Eq,
      Show
