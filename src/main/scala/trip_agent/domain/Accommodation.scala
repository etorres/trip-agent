package es.eriktorr
package trip_agent.domain

import cats.derived.*
import cats.{Eq, Show}
import io.circe.{Decoder, Encoder}
import org.typelevel.cats.time.instances.zoneddatetime.given
import sttp.tapir.Schema

import java.time.ZonedDateTime

final case class Accommodation(
    id: Int,
    name: String,
    neighborhood: String,
    checkin: ZonedDateTime,
    checkout: ZonedDateTime,
    pricePerNight: Int,
) derives Eq,
      Show,
      Schema,
      Encoder,
      Decoder
