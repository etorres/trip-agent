package es.eriktorr
package trip_agent.application

import trip_agent.application.agents.{
  AccommodationsSearchAgent,
  FlightsSearchAgent,
  MailWriterAgent,
}
import trip_agent.domain.*
import trip_agent.domain.RequestId.given
import trip_agent.domain.TripSearch.findEmail

import cats.derived.*
import cats.effect.IO
import cats.implicits.{catsSyntaxEitherId, showInterpolator}
import cats.{Eq, Show}
import io.circe.{Decoder, Encoder}
import monocle.syntax.all.*
import sttp.tapir.Schema
import workflows4s.wio
import workflows4s.wio.{SignalDef, WorkflowContext}

final class TripSearchWorkflow(
    accommodationsSearchAgent: AccommodationsSearchAgent,
    flightsSearchAgent: FlightsSearchAgent,
    mailWriterAgent: MailWriterAgent,
    mailSender: MailSender,
    bookingService: BookingService,
):
  import TripSearchWorkflow.*
  import TripSearchWorkflow.TripSearchContext.*

  private val startSearch: WIO[
    Any,
    TripSearchError.MissingEmail.type,
    TripSearchState.Started,
  ] =
    WIO
      .handleSignal(TripSearchSignal.findTrip)
      .using[Any]
      .purely: (_, request) =>
        TripSearchEvent.Started(request.requestId, request.question)
      .handleEventWithError[TripSearchError.MissingEmail.type, TripSearchState.Started]:
        (_, event) =>
          event match
            case TripSearchEvent.Started(requestId, question) if findEmail(question).isDefined =>
              TripSearchState.Started(requestId, question).asRight
            case _ => TripSearchError.MissingEmail.asLeft
      .voidResponse
      .autoNamed

  private val findAccommodations: WIO[
    TripSearchState.Started,
    TripSearchError.NotSettled.type,
    TripSearchState.Found,
  ] =
    findTrip(
      findTripUsing = input =>
        accommodationsSearchAgent
          .accommodationsFor(input.question)
          .map: accommodations =>
            TripSearchEvent.Found(accommodations, List.empty),
      handleErrorIn = (input, event) => foundTripFrom(input, event, event.accommodations),
      step = TripSearchStep("Find Accommodations", None),
    )

  private val findFlights: WIO[
    TripSearchState.Started,
    TripSearchError.NotSettled.type,
    TripSearchState.Found,
  ] =
    findTrip(
      findTripUsing = input =>
        flightsSearchAgent
          .flightsFor(input.question)
          .map: flights =>
            TripSearchEvent.Found(List.empty, flights),
      handleErrorIn = (input, event) => foundTripFrom(input, event, event.flights),
      step = TripSearchStep("Find Flights", None),
    )

  @SuppressWarnings(Array("org.wartremover.warts.Null"))
  private def findTrip(
      findTripUsing: TripSearchState.Started => IO[TripSearchEvent.Found],
      handleErrorIn: (TripSearchState.Started, TripSearchEvent.Found) => Either[
        TripSearchError.NotSettled.type,
        TripSearchState.Found,
      ],
      step: TripSearchStep,
  ): WIO[
    TripSearchState.Started,
    TripSearchError.NotSettled.type,
    TripSearchState.Found,
  ] =
    import scala.language.unsafeNulls
    WIO
      .runIO[TripSearchState.Started]: input =>
        findTripUsing(input)
      .handleEventWithError[
        TripSearchError.NotSettled.type,
        TripSearchState.Found,
      ](handleErrorIn)
      .named(
        name = step.name,
        description = step.description.orNull,
      )

  private def foundTripFrom[A](
      input: TripSearchState.Started,
      event: TripSearchEvent.Found,
      items: List[A],
  ): Either[TripSearchError.NotSettled.type, TripSearchState.Found] =
    if items.nonEmpty then
      TripSearchState
        .Found(
          input.requestId,
          input.question,
          event.accommodations,
          event.flights,
        )
        .asRight
    else TripSearchError.NotSettled.asLeft

  @SuppressWarnings(Array("org.wartremover.warts.AsInstanceOf"))
  private val findInParallel: WIO[
    TripSearchState.Started,
    TripSearchError.NotSettled.type,
    TripSearchState.Found,
  ] =
    WIO.parallel
      .taking[TripSearchState.Started]
      .withInterimState[TripSearchState.Found]: initial =>
        TripSearchState.Found(
          initial.requestId,
          initial.question,
          List.empty,
          List.empty,
        )
      .withElement(
        logic = findAccommodations,
        incorporatedWith = (interimState, pathState) =>
          val found = pathState.asInstanceOf[TripSearchState.Found]
          interimState.focus(_.accommodations).modify(_ ++ found.accommodations),
      )
      .withElement(
        logic = findFlights,
        incorporatedWith = (interimState, pathState) =>
          val found = pathState.asInstanceOf[TripSearchState.Found]
          interimState.focus(_.flights).modify(_ ++ found.flights),
      )
      .producingOutputWith: (aOut, bOut) =>
        aOut
          .focus(_.accommodations)
          .modify(_ ++ bOut.accommodations)
          .focus(_.flights)
          .modify(_ ++ bOut.flights)

  @SuppressWarnings(Array("org.wartremover.warts.AsInstanceOf"))
  private val sendEmail: WIO[
    TripSearchState.Found,
    Nothing,
    TripSearchState.Sent,
  ] =
    WIO
      .runIO[TripSearchState.Found]: input =>
        for
          (recipientEmail, emailBody) <-
            mailWriterAgent
              .writeEmail(
                accommodations = input.accommodations,
                flights = input.flights,
                question = input.question,
                requestId = input.requestId,
              )
          _ <- mailSender.sendEmail(
            to = recipientEmail,
            subject = emailSubjectFrom(input.requestId),
            content = emailBody,
          )
        yield TripSearchEvent.Sent(recipientEmail)
      .handleEvent[
        TripSearchState.Sent,
      ]: (input, event) =>
        val sent = event.asInstanceOf[TripSearchEvent.Sent]
        TripSearchState
          .Sent(
            sent.recipientEmail,
            input.accommodations,
            input.flights,
          )
      .autoNamed()

  private val processBooking: WIO[
    TripSearchState.Sent,
    TripSearchError.Rejected.type,
    TripSearchState.Booked,
  ] =
    WIO
      .handleSignal(TripSearchSignal.bookTrip)
      .using[TripSearchState.Sent]
      .withSideEffects: (input, event) =>
        IO.pure(event.approved)
          .ifM(
            ifTrue = bookingService.book(
              input.email,
              input.accommodations,
              input.flights,
            ),
            ifFalse = IO.unit,
          )
          .map: _ =>
            TripSearchEvent.Booked(event.approved)
      .handleEventWithError[
        TripSearchError.Rejected.type,
        TripSearchState.Booked,
      ]: (_, event) =>
        event match
          case TripSearchEvent.Booked(approved) if approved =>
            TripSearchState.Booked().asRight
          case _ => TripSearchError.Rejected.asLeft
      .voidResponse
      .autoNamed

  private val completeSearch: WIO[
    TripSearchState.Booked,
    Nothing,
    TripSearchState.Completed,
  ] =
    WIO.pure
      .makeFrom[TripSearchState.Booked]
      .value(identity)
      .autoNamed

  private val cancelSearch: WIO[
    (TripSearchState, TripSearchError),
    Nothing,
    TripSearchState.Canceled,
  ] =
    WIO.pure
      .makeFrom[(TripSearchState, TripSearchError)]
      .value[TripSearchState.Canceled]: (state, error) =>
        TripSearchState.Canceled(
          state = state,
          reason = error,
        )
      .autoNamed

  val workflow: WIO.Initial =
    (
      startSearch >>>
        findInParallel >>>
        sendEmail >>>
        processBooking >>>
        completeSearch
    ).handleErrorWith(cancelSearch)
end TripSearchWorkflow

object TripSearchWorkflow:
  enum TripSearchState derives Encoder, Eq, Show:
    case Empty
    case Started(requestId: RequestId, question: String)
    case Found(
        requestId: RequestId,
        question: String,
        accommodations: List[Accommodation],
        flights: List[Flight],
    )
    case Sent(
        email: String,
        accommodations: List[Accommodation],
        flights: List[Flight],
    )
    case Booked()
    case Canceled(state: TripSearchState, reason: TripSearchError)
  end TripSearchState

  object TripSearchState:
    type Completed = TripSearchState.Booked

  object TripSearchSignal:
    val findTrip: SignalDef[FindTrip, Unit] = SignalDef()
    val bookTrip: SignalDef[BookTrip, Unit] = SignalDef()
    final case class FindTrip(requestId: RequestId, question: String) derives Schema, Decoder
    final case class BookTrip(approved: Boolean) derives Schema, Decoder
  end TripSearchSignal

  enum TripSearchEvent:
    case Started(requestId: RequestId, question: String)
    case Found(accommodations: List[Accommodation], flights: List[Flight])
    case Sent(recipientEmail: String)
    case Booked(approved: Boolean)
    case Canceled
  end TripSearchEvent

  enum TripSearchError derives Encoder:
    case MissingEmail
    case NotSettled
    case Rejected
  end TripSearchError

  object TripSearchContext extends WorkflowContext:
    override type Event = TripSearchEvent
    override type State = TripSearchState
  end TripSearchContext

  final private case class TripSearchStep(
      name: String,
      description: Option[String],
  )

  def emailSubjectFrom(requestId: RequestId): String =
    show"Your trip is waiting for you! Request ID: ${requestId.value}"
end TripSearchWorkflow
