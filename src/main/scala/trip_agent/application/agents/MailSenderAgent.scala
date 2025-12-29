package es.eriktorr
package trip_agent.application.agents

import trip_agent.domain.{Accommodation, Flight}

import cats.effect.IO
import cats.implicits.showInterpolator

trait MailSenderAgent:
  def sendEmail(
      accommodations: List[Accommodation],
      flights: List[Flight],
      question: String,
  ): IO[String]

object MailSenderAgent:
  def impl: MailSenderAgent =
    (
        accommodations: List[Accommodation],
        flights: List[Flight],
        question: String,
    ) =>
      IO.println(
        show"Sending email with accommodations: $accommodations, flights: $flights, and question=$question",
      ) *>
        IO.pure("hello@example.org") // TODO
