package es.eriktorr
package trip_agent.domain

import cats.derived.*
import cats.{Eq, Show}
import io.circe.{Decoder, Encoder}
import io.hypersistence.tsid.TSID
import sttp.tapir.Schema

import scala.util.Try

final case class RequestId(
    value: TSID,
) derives Eq,
      Show,
      Schema,
      Encoder,
      Decoder

object RequestId:
  given Eq[TSID] = Eq.fromUniversalEquals

  given Show[TSID] = Show.fromToString

  given Schema[TSID] = Schema.string

  given Encoder[TSID] =
    Encoder.encodeString
      .contramap[TSID](_.toString)

  given Decoder[TSID] =
    Decoder.decodeString.emapTry: value =>
      Try(TSID.from(value))
