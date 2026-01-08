package es.eriktorr
package trip_agent.application

import trip_agent.application.FakeMailSender.MailSenderState
import trip_agent.domain.Email

import cats.effect.{IO, Ref}

final class FakeMailSender(
    stateRef: Ref[IO, MailSenderState],
) extends MailSender:
  override def send(email: Email): IO[Unit] =
    stateRef.update: currentState =>
      currentState.copy(
        sentMails = email :: currentState.sentMails,
      )

object FakeMailSender:
  final case class MailSenderState(
      sentMails: List[Email],
  )

  object MailSenderState:
    val empty: MailSenderState =
      MailSenderState(List.empty)
