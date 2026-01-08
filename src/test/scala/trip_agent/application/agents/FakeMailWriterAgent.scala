package es.eriktorr
package trip_agent.application.agents

import trip_agent.application.agents.FakeMailWriterAgent.MailWriterAgentState
import trip_agent.domain.*

import cats.effect.{IO, Ref}

final class FakeMailWriterAgent(
    stateRef: Ref[IO, MailWriterAgentState],
) extends MailWriterAgent:
  override def writeEmail(
      accommodations: List[Accommodation],
      flights: List[Flight],
      request: TripRequest,
  ): IO[(Email, List[TripOption])] =
    stateRef
      .update: currentState =>
        currentState.copy(
          writtenMails = (accommodations, flights, request) :: currentState.writtenMails,
        )
      .map: _ =>
        Email(
          messageId = Email.MessageId(request.requestId.value),
          recipient = FakeMailWriterAgent.findEmailUnsafe(request.question),
          subject = MailWriterAgent.emailSubjectFrom(request.requestId),
          body = FakeMailWriterAgent.emailBody,
        ) -> FakeMailWriterAgent.tripOptionsFrom(accommodations, flights)

object FakeMailWriterAgent:
  final case class MailWriterAgentState(
      writtenMails: List[(List[Accommodation], List[Flight], TripRequest)],
  )

  object MailWriterAgentState:
    val empty: MailWriterAgentState =
      MailWriterAgentState(List.empty)

  @SuppressWarnings(Array("org.wartremover.warts.Throw"))
  def findEmailUnsafe(question: String): Email.Address =
    Email
      .findEmail(question)
      .flatMap(Email.Address.fromString(_).toOption)
      .getOrElse(throw IllegalArgumentException("Missing email address"))

  def tripOptionsFrom(
      accommodations: List[Accommodation],
      flights: List[Flight],
  ): List[TripOption] =
    for
      accommodation <- accommodations
      flight <- flights
    yield TripOption(accommodation.id, flight.id)

  lazy val emailBody: Email.Body = Email.Body.applyUnsafe("Email Body")
