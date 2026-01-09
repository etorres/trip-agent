package es.eriktorr
package trip_agent.application

import trip_agent.application.FakeMailSender.MailSenderState
import trip_agent.domain.Email
import trip_agent.spec.SimulatedFailure
import trip_agent.spec.refined.Types.FailureRate

import cats.effect.std.Random
import cats.effect.{IO, Ref}
import cats.mtl.Raise

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
