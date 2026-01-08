package es.eriktorr
package trip_agent.application

import trip_agent.application.BookingService.BookingServiceError
import trip_agent.application.MailSender.MailSenderError
import trip_agent.application.agents.{
  AccommodationsSearchAgent,
  FlightsSearchAgent,
  MailWriterAgent,
}
import trip_agent.domain.*
import trip_agent.domain.Email.findEmail
import trip_agent.infrastructure.network.PekkoCirceSerializer

import cats.derived.*
import cats.effect.IO
import cats.implicits.{catsSyntaxEitherId, catsSyntaxEq}
import cats.{Eq, Show}
import io.circe.{Codec, Decoder, Encoder}
import monocle.syntax.all.*
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
        TripSearchEvent.Started(request.request)
      .handleEventWithError[
        TripSearchError.MissingEmail.type,
        TripSearchState.Started,
      ]: (_, event) =>
        event match
          case TripSearchEvent.Started(request) if findEmail(request.question).isDefined =>
            TripSearchState.Started(request).asRight
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
          .accommodationsFor(input.request.question)
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
          .flightsFor(input.request.question)
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
          input.request,
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
          initial.request,
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
          (email, tripOptions) <-
            mailWriterAgent
              .writeEmail(
                accommodations = input.accommodations,
                flights = input.flights,
                request = input.request,
              )
          _ <- mailSender.send(email).handleErrorWith {
            case _: MailSenderError.DuplicatedMessageId => IO.unit
            case other => IO.raiseError(other)
          }
        yield TripSearchEvent.Sent(
          email.recipient,
          tripOptions,
        )
      .handleEvent[
        TripSearchState.Sent,
      ]: (_, event) =>
        val sent = event.asInstanceOf[TripSearchEvent.Sent]
        TripSearchState.Sent(
          sent.recipient,
          sent.options,
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
        val tripOption = event.confirmation.tripOption
        IO.pure(input.options.exists(_ === tripOption))
          .ifM(
            ifTrue = bookingService
              .book(event.confirmation)
              .handleErrorWith {
                case _: BookingServiceError.DuplicatedBookingId => IO.unit
                case other => IO.raiseError(other)
              }
              .map: _ =>
                TripSearchEvent.Booked(event.confirmation),
            ifFalse = IO.pure(TripSearchEvent.Canceled),
          )
      .handleEventWithError[
        TripSearchError.Rejected.type,
        TripSearchState.Booked,
      ]: (_, event) =>
        event match
          case TripSearchEvent.Booked(confirmation) =>
            TripSearchState.Booked(confirmation).asRight
          case _ => TripSearchError.Rejected.asLeft
      .produceResponse: (_, event) =>
        event match
          case TripSearchEvent.Booked(confirmation) =>
            BookingResponse(accepted = true, Some(confirmation.bookingId))
          case _ => BookingResponse(accepted = false, None)
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
    case Started(request: TripRequest)
    case Found(
        request: TripRequest,
        accommodations: List[Accommodation],
        flights: List[Flight],
    )
    case Sent(
        recipient: Email.Address,
        options: List[TripOption],
    )
    case Booked(
        confirmation: BookingConfirmation,
    )
    case Canceled(state: TripSearchState, reason: TripSearchError)
  end TripSearchState

  object TripSearchState:
    type Completed = TripSearchState.Booked

  object TripSearchSignal:
    val findTrip: SignalDef[FindTrip, Unit] = SignalDef()
    val bookTrip: SignalDef[BookTrip, BookingResponse] = SignalDef()
    final case class FindTrip(request: TripRequest) derives Decoder
    final case class BookTrip(
        confirmation: BookingConfirmation,
    ) derives Decoder
  end TripSearchSignal

  enum TripSearchEvent derives Codec.AsObject:
    case Started(request: TripRequest)
    case Found(
        accommodations: List[Accommodation],
        flights: List[Flight],
    )
    case Sent(
        recipient: Email.Address,
        options: List[TripOption],
    )
    case Booked(
        confirmation: BookingConfirmation,
    )
    case Canceled
  end TripSearchEvent

  object TripSearchEvent:
    final class PekkoSerializer extends PekkoCirceSerializer[TripSearchEvent]:
      override def identifier: Int = 28648

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
end TripSearchWorkflow
