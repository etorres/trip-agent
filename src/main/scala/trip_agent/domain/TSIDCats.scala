package es.eriktorr
package trip_agent.domain

import cats.{Eq, Show}
import io.circe.{Decoder, Encoder}
import io.hypersistence.tsid.TSID

import scala.util.Try

object TSIDCats:
  given Eq[TSID] = Eq.fromUniversalEquals

  given Show[TSID] = Show.fromToString

  given Encoder[TSID] =
    Encoder.encodeString
      .contramap[TSID](_.toString)

  given Decoder[TSID] =
    Decoder.decodeString.emapTry: value =>
      Try(TSID.from(value))
