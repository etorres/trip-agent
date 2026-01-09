package es.eriktorr
package trip_agent.application

import trip_agent.domain.Email
import trip_agent.domain.TSIDCats.given
import trip_agent.infrastructure.data.error.HandledError

import cats.effect.{IO, Ref}
import cats.implicits.{catsSyntaxEq, showInterpolator}
import cats.mtl.Raise
import cats.mtl.implicits.given
import org.typelevel.log4cats.StructuredLogger

trait MailSender:
  def send(
      email: Email,
  )(using Raise[IO, MailSender.Error]): IO[Unit]

object MailSender:
  def impl(
      from: Email.Address,
      stateRef: Ref[IO, MailSender.State],
  )(using logger: StructuredLogger[IO]): MailSender =
    new MailSender:
      override def send(
          email: Email,
      )(using Raise[IO, Error]): IO[Unit] =
        stateRef.flatModify: currentState =>
          val (effect, update) =
            currentState.sentMessages.find(_ === email.messageId) match
              case Some(value) =>
                val effect = MailSender.Error
                  .DuplicatedMessageId(value)
                  .raise[IO, Unit]
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

  final case class State(
      sentMessages: List[Email.MessageId],
  )

  object State:
    val empty: State = State(List.empty)

  enum Error(val message: String) extends HandledError(message):
    case DuplicatedMessageId(messageId: Email.MessageId)
        extends Error(show"Message with ID ${messageId.value} has already been sent")
