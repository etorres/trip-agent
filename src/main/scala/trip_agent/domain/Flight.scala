package es.eriktorr
package trip_agent.domain

import cats.derived.*
import cats.{Eq, Show}
import io.circe.{Decoder, Encoder}
import org.typelevel.cats.time.instances.zoneddatetime.given
import sttp.tapir.Schema

import java.time.ZonedDateTime

final case class Flight(
    id: Int,
    from: String,
    to: String,
    departure: ZonedDateTime,
    arrival: ZonedDateTime,
    price: Int,
) derives Eq,
      Show,
      Schema,
      Encoder,
      Decoder
