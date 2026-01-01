package es.eriktorr
package trip_agent.application.agents

import trip_agent.application.agents.FakeMailWriterAgent.{findEmailUnsafe, MailWriterAgentState}
import trip_agent.domain.{Accommodation, Flight, RequestId, TripSearch}

import cats.effect.{IO, Ref}

final class FakeMailWriterAgent(
    stateRef: Ref[IO, MailWriterAgentState],
) extends MailWriterAgent:
  override def writeEmail(
      accommodations: List[Accommodation],
      flights: List[Flight],
      question: String,
      requestId: RequestId,
  ): IO[(String, String)] =
    stateRef
      .update: currentState =>
        currentState.copy(
          writtenMails = (accommodations, flights, question, requestId) :: currentState.writtenMails,
        )
      .map: _ =>
        findEmailUnsafe(question) -> FakeMailWriterAgent.emailBody

object FakeMailWriterAgent:
  final case class MailWriterAgentState(
      writtenMails: List[(List[Accommodation], List[Flight], String, RequestId)],
  )

  object MailWriterAgentState:
    val empty: MailWriterAgentState =
      MailWriterAgentState(List.empty)

  @SuppressWarnings(Array("org.wartremover.warts.Throw"))
  def findEmailUnsafe(question: String): String =
    TripSearch
      .findEmail(question)
      .getOrElse(throw IllegalArgumentException("Missing email address"))

  lazy val emailBody = "Email Body"
