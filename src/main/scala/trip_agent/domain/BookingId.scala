package es.eriktorr
package trip_agent.domain

import trip_agent.domain.TSIDCats.given

import cats.derived.*
import cats.{Eq, Show}
import io.circe.Codec
import io.hypersistence.tsid.TSID

final case class BookingId(
    value: TSID,
) derives Codec,
      Eq,
      Show
