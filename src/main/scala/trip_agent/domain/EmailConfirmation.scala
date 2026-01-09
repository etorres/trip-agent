package es.eriktorr
package trip_agent.domain

import cats.derived.*
import cats.{Eq, Show}
import io.circe.Codec

final case class EmailConfirmation(
    recipient: Option[Email.Address],
    options: List[TripOption],
) derives Codec,
      Eq,
      Show

object EmailConfirmation:
  lazy val notSent: EmailConfirmation =
    EmailConfirmation(
      recipient = None,
      options = List.empty,
    )
