package es.eriktorr
package trip_agent.domain

import cats.derived.*
import cats.{Eq, Show}
import io.circe.{Decoder, Encoder}
import sttp.tapir.Schema

final case class Accommodation(
    id: String,
) derives Eq,
      Show,
      Schema,
      Encoder,
      Decoder
