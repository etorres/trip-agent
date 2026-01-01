package es.eriktorr
package trip_agent.application

import trip_agent.application.FakeBookingService.BookingServiceState
import trip_agent.application.TripSearchWorkflow.{
  TripSearchContext,
  TripSearchError,
  TripSearchSignal,
  TripSearchState,
}
import trip_agent.application.agents.FakeAccommodationsSearchAgent.AccommodationsSearchAgentState
import trip_agent.application.agents.FakeFlightsSearchAgent.FlightsSearchAgentState
import trip_agent.application.agents.FakeMailWriterAgent.MailWriterAgentState
import trip_agent.application.agents.{
  FakeAccommodationsSearchAgent,
  FakeFlightsSearchAgent,
  FakeMailWriterAgent,
}
import trip_agent.domain.RequestId.given
import trip_agent.domain.TripSearchGenerators.{
  accommodationsGen,
  flightsGen,
  questionGen,
  requestIdGen,
}
import trip_agent.domain.{Accommodation, Flight, RequestId, TripSearch}
import trip_agent.spec.StringGenerators.alphaNumericStringBetween

import cats.derived.*
import cats.effect.{IO, Ref, Resource}
import cats.implicits.toShow
import cats.{Eq, Show}
import es.eriktorr.trip_agent.application.FakeMailSender.MailSenderState
import org.scalacheck.Gen
import weaver.scalacheck.Checkers
import weaver.{Expectations, IOSuite, Log}
import workflows4s.runtime.instanceengine.WorkflowInstanceEngine
import workflows4s.runtime.registry.InMemoryWorkflowRegistry
import workflows4s.runtime.wakeup.SleepingKnockerUpper
import workflows4s.runtime.{InMemoryRuntime, InMemoryWorkflowInstance}

object TripSearchWorkflowSuite extends IOSuite with Checkers:
  test("should find a trip"): (engine, log) =>
    testOperationWith(engine, log)(
      bookTripTestCaseGen,
      _.deliverSignal(
        TripSearchSignal.bookTrip,
        TripSearchSignal.BookTrip(approved = true),
      ).void,
    )

  test("should fail with an error when starting a search without email"): (engine, log) =>
    testStateWith(engine, log)(
      missingEmailTestCaseGen,
    )

  test("should fail with an error when the search is incomplete"): (engine, log) =>
    testStateWith(engine, log)(
      incompleteTestCaseGen,
    )

  test("should reject a trip search when the booking is not approved"): (engine, log) =>
    testOperationWith(engine, log)(
      rejectedTestCaseGen,
      _.deliverSignal(
        TripSearchSignal.bookTrip,
        TripSearchSignal.BookTrip(approved = false),
      ).void,
    )

  private val testStateWith
      : (WorkflowInstanceEngine, Log[IO]) => Gen[TestCase] => IO[Expectations] =
    (engine, log) => testCaseGen => testOperationWith(engine, log)(testCaseGen, _ => IO.unit)

  private val testOperationWith: (WorkflowInstanceEngine, Log[IO]) => (
      Gen[TestCase],
      InMemoryWorkflowInstance[TripSearchContext.Ctx] => IO[Unit],
  ) => IO[Expectations] =
    (engine, log) =>
      (testCaseGen, testee) =>
        forall(testCaseGen): testCase =>
          testResources(engine, testCase.accommodations, testCase.flights).use:
            (runtime, writtenMailsStateRef, sentMailsStateRef, bookingsStateRef) =>
              for
                workflowInstance <- runtime.createInstance(testCase.requestId.value.show)
                _ <- workflowInstance.deliverSignal(
                  TripSearchSignal.findTrip,
                  TripSearchSignal.FindTrip(testCase.requestId, testCase.question),
                )
                _ <- workflowInstance
                  .queryState()
                  .flatMap: state =>
                    log.info(s"State after find trip signal is: $state")
                _ <- testee(workflowInstance)
                finalState <- workflowInstance.queryState()
                finalMailWriterAgentState <- writtenMailsStateRef.get
                finalMailSenderState <- sentMailsStateRef.get
                finalBookingServiceState <- bookingsStateRef.get
              yield (
                expect.eql(testCase.expectedState, finalState) ||
                  exists(testCase.otherPossibleState)(state => expect.eql(state, finalState))
              ) &&
                expect.eql(testCase.expectedWrittenMails, finalMailWriterAgentState.writtenMails) &&
                expect.eql(testCase.expectedSentMails, finalMailSenderState.sentMails) &&
                expect.eql(testCase.expectedBooking, finalBookingServiceState.bookings)

  override type Res = WorkflowInstanceEngine

  override def sharedResource: Resource[IO, Res] =
    for
      knockerUpper <- SleepingKnockerUpper.create()
      registry <- InMemoryWorkflowRegistry().toResource
      engine = WorkflowInstanceEngine.default(knockerUpper, registry)
    yield engine

  private def testResources(
      engine: WorkflowInstanceEngine,
      accommodations: Map[String, List[Accommodation]],
      flights: Map[String, List[Flight]],
  ) =
    for
      (tripSearchWorkflow, writtenMailsStateRef, sentMailsStateRef, bookingsStateRef) <-
        Resource.eval:
          for
            accommodationsStateRef <- Ref.of[IO, AccommodationsSearchAgentState](
              AccommodationsSearchAgentState.empty.copy(accommodations),
            )
            flightsStateRef <- Ref.of[IO, FlightsSearchAgentState](
              FlightsSearchAgentState.empty.copy(flights),
            )
            writtenMailsStateRef <- Ref.of[IO, MailWriterAgentState](
              MailWriterAgentState.empty,
            )
            sentMailsStateRef <- Ref.of[IO, MailSenderState](
              MailSenderState.empty,
            )
            bookingsStateRef <- Ref.of[IO, BookingServiceState](
              BookingServiceState.empty,
            )
            tripSearchWorkflow = TripSearchWorkflow(
              accommodationsSearchAgent = FakeAccommodationsSearchAgent(accommodationsStateRef),
              flightsSearchAgent = FakeFlightsSearchAgent(flightsStateRef),
              mailWriterAgent = FakeMailWriterAgent(writtenMailsStateRef),
              mailSender = FakeMailSender(sentMailsStateRef),
              bookingService = FakeBookingService(bookingsStateRef),
            )
          yield (tripSearchWorkflow, writtenMailsStateRef, sentMailsStateRef, bookingsStateRef)
      runtime <- InMemoryRuntime
        .default[TripSearchContext.Ctx](
          workflow = tripSearchWorkflow.workflow,
          initialState = TripSearchState.Empty,
          engine = engine,
        )
        .toResource
    yield (runtime, writtenMailsStateRef, sentMailsStateRef, bookingsStateRef)

  final private case class TestCase(
      requestId: RequestId,
      question: String,
      accommodations: Map[String, List[Accommodation]],
      flights: Map[String, List[Flight]],
      expectedState: TripSearchState,
      otherPossibleState: Option[TripSearchState],
      expectedWrittenMails: List[(List[Accommodation], List[Flight], String, RequestId)],
      expectedSentMails: List[(String, String, String)],
      expectedBooking: List[(String, List[Accommodation], List[Flight])],
  ) derives Eq,
        Show

  private lazy val bookTripTestCaseGen =
    for
      requestId <- requestIdGen
      question <- questionGen
      accommodations <- accommodationsGen
      flights <- flightsGen
      emailAddress = FakeMailWriterAgent.findEmailUnsafe(question)
    yield TestCase(
      requestId = requestId,
      question = question,
      accommodations = Map(question -> accommodations),
      flights = Map(question -> flights),
      expectedState = TripSearchState.Booked(),
      otherPossibleState = None,
      expectedWrittenMails = List((accommodations, flights, question, requestId)),
      expectedSentMails = List(
        (
          emailAddress,
          TripSearchWorkflow.emailSubjectFrom(requestId),
          FakeMailWriterAgent.emailBody,
        ),
      ),
      expectedBooking = List((emailAddress, accommodations, flights)),
    )

  private lazy val missingEmailTestCaseGen =
    for
      requestId <- requestIdGen
      question <- alphaNumericStringBetween(3, 12)
        .retryUntil: question =>
          TripSearch.findEmail(question).isEmpty
    yield TestCase(
      requestId = requestId,
      question = question,
      accommodations = Map.empty,
      flights = Map.empty,
      expectedState = TripSearchState.Canceled(
        state = TripSearchState.Empty,
        reason = TripSearchError.MissingEmail,
      ),
      otherPossibleState = None,
      expectedWrittenMails = List.empty,
      expectedSentMails = List.empty,
      expectedBooking = List.empty,
    )

  private lazy val incompleteTestCaseGen =
    for
      requestId <- requestIdGen
      question <- questionGen
      (accommodations, flights) <- Gen.frequency(
        1 -> accommodationsGen.map(_ -> List.empty),
        1 -> flightsGen.map(List.empty -> _),
        1 -> Gen.const(List.empty -> List.empty),
      )
    yield TestCase(
      requestId = requestId,
      question = question,
      accommodations = Map(question -> accommodations),
      flights = Map(question -> flights),
      expectedState = TripSearchState.Canceled(
        state = TripSearchState.Started(requestId, question),
        reason = TripSearchError.NotSettled,
      ),
      otherPossibleState = Some(
        TripSearchState.Canceled(
          state = TripSearchState.Found(requestId, question, accommodations, flights),
          reason = TripSearchError.NotSettled,
        ),
      ),
      expectedWrittenMails = List.empty,
      expectedSentMails = List.empty,
      expectedBooking = List.empty,
    )

  private lazy val rejectedTestCaseGen =
    for
      requestId <- requestIdGen
      question <- questionGen
      accommodations <- accommodationsGen
      flights <- flightsGen
      emailAddress = FakeMailWriterAgent.findEmailUnsafe(question)
    yield TestCase(
      requestId = requestId,
      question = question,
      accommodations = Map(question -> accommodations),
      flights = Map(question -> flights),
      expectedState = TripSearchState.Canceled(
        state = TripSearchState.Sent(emailAddress, accommodations, flights),
        reason = TripSearchError.Rejected,
      ),
      otherPossibleState = None,
      expectedWrittenMails = List((accommodations, flights, question, requestId)),
      expectedSentMails = List(
        (
          emailAddress,
          TripSearchWorkflow.emailSubjectFrom(requestId),
          FakeMailWriterAgent.emailBody,
        ),
      ),
      expectedBooking = List.empty,
    )
