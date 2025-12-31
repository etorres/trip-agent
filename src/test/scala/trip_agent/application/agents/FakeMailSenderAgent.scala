package es.eriktorr
package trip_agent.application.agents

import trip_agent.application.agents.FakeMailSenderAgent.{findEmailUnsafe, MailSenderAgentState}
import trip_agent.domain.{Accommodation, Flight, TripSearch}

import cats.effect.{IO, Ref}

final class FakeMailSenderAgent(
    stateRef: Ref[IO, MailSenderAgentState],
) extends MailSenderAgent:
  override def sendEmail(
      accommodations: List[Accommodation],
      flights: List[Flight],
      question: String,
      requestId: String,
  ): IO[String] =
    stateRef
      .update: currentState =>
        currentState.copy(
          sentMails = (accommodations, flights, question, requestId) :: currentState.sentMails,
        )
      .map: _ =>
        findEmailUnsafe(question)

object FakeMailSenderAgent:
  final case class MailSenderAgentState(
      sentMails: List[(List[Accommodation], List[Flight], String, String)],
  )

  object MailSenderAgentState:
    val empty: MailSenderAgentState =
      MailSenderAgentState(List.empty)

  @SuppressWarnings(Array("org.wartremover.warts.Throw"))
  def findEmailUnsafe(question: String): String =
    TripSearch
      .findEmail(question)
      .getOrElse(throw IllegalArgumentException("Missing email address"))
