package es.eriktorr
package trip_agent.domain

import cats.derived.*
import cats.{Eq, Show}
import io.circe.{Codec, Decoder, Encoder}
import org.typelevel.cats.time.instances.zoneddatetime.given

import java.time.ZonedDateTime

final case class Accommodation(
    id: Accommodation.Id,
    name: String,
    neighborhood: String,
    checkin: ZonedDateTime,
    checkout: ZonedDateTime,
    pricePerNight: Int,
) derives Codec,
      Eq,
      Show

object Accommodation:
  opaque type Id <: Int = Int

  object Id:
    def apply(value: Int): Id = value

    given Eq[Id] = Eq.fromUniversalEquals

    given Show[Id] = Show.fromToString

    given Codec[Id] =
      Codec.from(
        Decoder.decodeInt,
        Encoder.encodeInt,
      )
  end Id
