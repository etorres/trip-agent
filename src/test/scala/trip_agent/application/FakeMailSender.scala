package es.eriktorr
package trip_agent.application

import trip_agent.application.FakeMailSender.MailSenderState

import cats.effect.{IO, Ref}

final class FakeMailSender(
    stateRef: Ref[IO, MailSenderState],
) extends MailSender:
  override def sendEmail(
      to: String,
      subject: String,
      content: String,
  ): IO[Unit] =
    stateRef.update: currentState =>
      currentState.copy(
        sentMails = (to, subject, content) :: currentState.sentMails,
      )

object FakeMailSender:
  final case class MailSenderState(
      sentMails: List[(String, String, String)],
  )

  object MailSenderState:
    val empty: MailSenderState =
      MailSenderState(List.empty)
