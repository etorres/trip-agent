package es.eriktorr
package trip_agent.application

import trip_agent.domain.Email
import trip_agent.domain.TSIDCats.given
import trip_agent.infrastructure.data.error.HandledError

import cats.effect.{IO, Ref}
import cats.implicits.{catsSyntaxEq, showInterpolator}
import org.typelevel.log4cats.StructuredLogger

trait MailSender:
  def send(email: Email): IO[Unit]

object MailSender:
  def impl(
      from: Email.Address,
      stateRef: Ref[IO, MailSenderState],
  )(using logger: StructuredLogger[IO]): MailSender =
    (email: Email) =>
      stateRef.flatModify: currentState =>
        val (effect, update) =
          currentState.sentMessages.find(_ === email.messageId) match
            case Some(value) =>
              val effect = IO.raiseError[Unit](
                MailSenderError.DuplicatedMessageId(value),
              )
              val update = currentState.sentMessages
              effect -> update
            case None =>
              val effect = logger.info(s"""From: $from
                                          |To: ${email.recipient}
                                          |Subject: ${email.subject}
                                          |${email.body}""".stripMargin)
              val update = email.messageId :: currentState.sentMessages
              effect -> update
        (currentState.copy(update), effect)

  final case class MailSenderState(
      sentMessages: List[Email.MessageId],
  )

  object MailSenderState:
    val empty: MailSenderState =
      MailSenderState(List.empty)

  sealed abstract class MailSenderError(message: String) extends HandledError(message)

  object MailSenderError:
    final case class DuplicatedMessageId(messageId: Email.MessageId)
        extends MailSenderError(show"Message with ID ${messageId.value} has already been sent")
