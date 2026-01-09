package es.eriktorr
package trip_agent.application

import trip_agent.application.FakeMailSender.MailSenderState
import trip_agent.domain.Email
import trip_agent.spec.SimulatedFailure
import trip_agent.spec.refined.Types.FailureRate

import cats.effect.std.Random
import cats.effect.{IO, Ref}
import cats.implicits.catsSyntaxEq
import cats.mtl.Raise
import cats.mtl.implicits.given

final class FakeMailSender(
    stateRef: Ref[IO, MailSenderState],
    failureRate: FailureRate = FailureRate.alwaysSucceed,
)(using random: Random[IO])
    extends MailSender:
  override def send(
      email: Email,
  )(using Raise[IO, MailSender.Error]): IO[Unit] =
    random.nextDouble.flatMap: roll =>
      if roll < failureRate
      then IO.raiseError(SimulatedFailure)
      else
        stateRef.flatModify: currentState =>
          val (effect, update) =
            currentState.sentMails.map(_.messageId).find(_ === email.messageId) match
              case Some(value) =>
                val effect = MailSender.Error
                  .DuplicatedMessageId(value)
                  .raise[IO, Unit]
                val update = currentState.sentMails
                effect -> update
              case None =>
                val effect = IO.unit
                val update = email :: currentState.sentMails
                effect -> update
          (currentState.copy(update), effect)

object FakeMailSender:
  final case class MailSenderState(
      sentMails: List[Email],
  )

  object MailSenderState:
    val empty: MailSenderState =
      MailSenderState(List.empty)
