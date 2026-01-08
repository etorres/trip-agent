package es.eriktorr
package trip_agent.application

import trip_agent.application.FakeBookingService.BookingServiceState
import trip_agent.application.FakeMailSender.MailSenderState
import trip_agent.application.TripSearchWorkflow.*
import trip_agent.application.agents.FakeAccommodationsSearchAgent.AccommodationsSearchAgentState
import trip_agent.application.agents.FakeFlightsSearchAgent.FlightsSearchAgentState
import trip_agent.application.agents.FakeMailWriterAgent.MailWriterAgentState
import trip_agent.application.agents.{
  FakeAccommodationsSearchAgent,
  FakeFlightsSearchAgent,
  FakeMailWriterAgent,
  MailWriterAgent,
}
import trip_agent.domain.*
import trip_agent.domain.TSIDCats.given
import trip_agent.domain.TripSearchGenerators.{
  accommodationsGen,
  flightsGen,
  tripOptionGen,
  tripRequestGen,
}
import trip_agent.spec.StringGenerators.alphaNumericStringBetween

import cats.derived.*
import cats.effect.{IO, Ref, Resource}
import cats.implicits.{catsStdShowForEither, catsSyntaxEitherId, catsSyntaxEq, toShow}
import cats.{Eq, Show}
import org.scalacheck.Gen
import weaver.scalacheck.Checkers
import weaver.{Expectations, IOSuite, Log}
import workflows4s.runtime.WorkflowInstance.UnexpectedSignal
import workflows4s.runtime.instanceengine.WorkflowInstanceEngine
import workflows4s.runtime.registry.InMemoryWorkflowRegistry
import workflows4s.runtime.wakeup.SleepingKnockerUpper
import workflows4s.runtime.{InMemoryRuntime, InMemoryWorkflowInstance}

object TripSearchWorkflowSuite extends IOSuite with Checkers:
  test("should find a trip"): (engine, log) =>
    testOperationWith[Either[UnexpectedSignal, BookingResponse]](engine, log)(
      bookTripTestCaseGen,
      (testCase, workflowInstance) =>
        workflowInstance
          .deliverSignal(
            TripSearchSignal.bookTrip,
            TripSearchSignal.BookTrip(testCase.unsafeConfirmation),
          ),
    )

  test("should fail with an error when starting a search without email"): (engine, log) =>
    testStateWith(engine, log)(
      missingEmailTestCaseGen,
    )

  test("should fail with an error when the search is incomplete"): (engine, log) =>
    testStateWith(engine, log)(
      incompleteTestCaseGen,
    )

  test("should reject a trip search when the booking include any error"): (engine, log) =>
    testOperationWith[Either[UnexpectedSignal, BookingResponse]](engine, log)(
      rejectedTestCaseGen,
      (testCase, workflowInstance) =>
        workflowInstance
          .deliverSignal(
            TripSearchSignal.bookTrip,
            TripSearchSignal.BookTrip(testCase.unsafeConfirmation),
          ),
    )

  private given Show[UnexpectedSignal] = Show.fromToString

  private val testStateWith
      : (WorkflowInstanceEngine, Log[IO]) => Gen[TestCase[Unit]] => IO[Expectations] =
    (engine, log) =>
      testCaseGen => testOperationWith[Unit](engine, log)(testCaseGen, (_, _) => IO.unit)

  private def testOperationWith[A: Show]: (WorkflowInstanceEngine, Log[IO]) => (
      Gen[TestCase[A]],
      (TestCase[A], InMemoryWorkflowInstance[TripSearchContext.Ctx]) => IO[A],
  ) => IO[Expectations] =
    (engine, log) =>
      (testCaseGen, testee) =>
        forall(testCaseGen): testCase =>
          testResources(engine, testCase.accommodations, testCase.flights).use:
            (runtime, writtenMailsStateRef, sentMailsStateRef, bookingsStateRef) =>
              for
                workflowInstance <- runtime.createInstance(
                  testCase.request.requestId.value.show,
                )
                _ <- workflowInstance.deliverSignal(
                  TripSearchSignal.findTrip,
                  TripSearchSignal.FindTrip(
                    testCase.request,
                  ),
                )
                _ <- workflowInstance
                  .queryState()
                  .flatMap: state =>
                    log.info(s"State after find trip signal is: $state")
                obtained <- testee(testCase, workflowInstance)
                finalState <- workflowInstance.queryState()
                finalMailWriterAgentState <- writtenMailsStateRef.get
                finalMailSenderState <- sentMailsStateRef.get
                finalBookingServiceState <- bookingsStateRef.get
              yield expect.same(testCase.expectedResult, obtained) &&
                (
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

  final private case class TestCase[A](
      request: TripRequest,
      accommodations: Map[String, List[Accommodation]],
      flights: Map[String, List[Flight]],
      maybeConfirmation: Option[BookingConfirmation],
      expectedState: TripSearchState,
      otherPossibleState: Option[TripSearchState],
      expectedWrittenMails: List[(List[Accommodation], List[Flight], TripRequest)],
      expectedSentMails: List[Email],
      expectedBooking: List[BookingConfirmation],
      expectedResult: A,
  ) derives Eq,
        Show:
    @SuppressWarnings(Array("org.wartremover.warts.OptionPartial"))
    def unsafeConfirmation: BookingConfirmation = maybeConfirmation.get

  private lazy val bookTripTestCaseGen =
    for
      request <- tripRequestGen()
      accommodations <- accommodationsGen
      flights <- flightsGen
      emailAddress = FakeMailWriterAgent.findEmailUnsafe(request.question)
      tripOptions = FakeMailWriterAgent.tripOptionsFrom(accommodations, flights)
      confirmation <- Gen
        .oneOf(tripOptions)
        .map: tripOption =>
          BookingConfirmation(
            BookingId(request.requestId.value),
            tripOption,
          )
    yield TestCase(
      request = request,
      accommodations = Map(request.question -> accommodations),
      flights = Map(request.question -> flights),
      maybeConfirmation = Some(confirmation),
      expectedState = TripSearchState.Booked(confirmation),
      otherPossibleState = None,
      expectedWrittenMails = List((accommodations, flights, request)),
      expectedSentMails = List(
        Email(
          messageId = Email.MessageId(request.requestId.value),
          recipient = emailAddress,
          subject = MailWriterAgent.emailSubjectFrom(request.requestId),
          body = FakeMailWriterAgent.emailBody,
        ),
      ),
      expectedBooking = List(confirmation),
      expectedResult = BookingResponse(
        accepted = true,
        bookingId = Some(BookingId(request.requestId.value)),
      ).asRight[UnexpectedSignal],
    )

  private lazy val missingEmailTestCaseGen =
    tripRequestGen(
      questionGen = alphaNumericStringBetween(3, 12)
        .retryUntil: question =>
          Email.findEmail(question).isEmpty,
    ).map: request =>
      TestCase(
        request = request,
        accommodations = Map.empty,
        flights = Map.empty,
        maybeConfirmation = None,
        expectedState = TripSearchState.Canceled(
          state = TripSearchState.Empty,
          reason = TripSearchError.MissingEmail,
        ),
        otherPossibleState = None,
        expectedWrittenMails = List.empty,
        expectedSentMails = List.empty,
        expectedBooking = List.empty,
        expectedResult = (),
      )

  private lazy val incompleteTestCaseGen =
    for
      request <- tripRequestGen()
      (accommodations, flights) <- Gen.frequency(
        1 -> accommodationsGen.map(_ -> List.empty),
        1 -> flightsGen.map(List.empty -> _),
        1 -> Gen.const(List.empty -> List.empty),
      )
    yield TestCase(
      request = request,
      accommodations = Map(request.question -> accommodations),
      flights = Map(request.question -> flights),
      maybeConfirmation = None,
      expectedState = TripSearchState.Canceled(
        state = TripSearchState.Started(request),
        reason = TripSearchError.NotSettled,
      ),
      otherPossibleState = Some(
        TripSearchState.Canceled(
          state = TripSearchState.Found(request, accommodations, flights),
          reason = TripSearchError.NotSettled,
        ),
      ),
      expectedWrittenMails = List.empty,
      expectedSentMails = List.empty,
      expectedBooking = List.empty,
      expectedResult = (),
    )

  private lazy val rejectedTestCaseGen =
    for
      request <- tripRequestGen()
      accommodations <- accommodationsGen
      flights <- flightsGen
      emailAddress = FakeMailWriterAgent.findEmailUnsafe(request.question)
      tripOptions = FakeMailWriterAgent.tripOptionsFrom(accommodations, flights)
      confirmation <-
        tripOptionGen
          .retryUntil(x => !tripOptions.exists(_ === x))
          .map: tripOption =>
            BookingConfirmation(
              BookingId(request.requestId.value),
              tripOption,
            )
    yield TestCase(
      request = request,
      accommodations = Map(request.question -> accommodations),
      flights = Map(request.question -> flights),
      maybeConfirmation = Some(confirmation),
      expectedState = TripSearchState.Canceled(
        state = TripSearchState.Sent(
          emailAddress,
          tripOptions,
        ),
        reason = TripSearchError.Rejected,
      ),
      otherPossibleState = None,
      expectedWrittenMails = List((accommodations, flights, request)),
      expectedSentMails = List(
        Email(
          messageId = Email.MessageId(request.requestId.value),
          recipient = emailAddress,
          subject = MailWriterAgent.emailSubjectFrom(request.requestId),
          body = FakeMailWriterAgent.emailBody,
        ),
      ),
      expectedBooking = List.empty,
      expectedResult = BookingResponse(
        accepted = false,
        bookingId = None,
      ).asRight[UnexpectedSignal],
    )
