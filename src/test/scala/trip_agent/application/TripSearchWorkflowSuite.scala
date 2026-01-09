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
import trip_agent.spec.refined.Types.FailureRate

import cats.derived.*
import cats.effect.{IO, Ref, Resource}
import cats.implicits.{catsStdShowForEither, catsSyntaxEitherId, catsSyntaxEq, toShow}
import cats.{Eq, Show}
import org.scalacheck.Gen
import org.typelevel.log4cats.StructuredLogger
import org.typelevel.log4cats.noop.NoOpLogger
import weaver.scalacheck.Checkers
import weaver.{Expectations, IOSuite, Log}
import workflows4s.runtime.WorkflowInstance.UnexpectedSignal
import workflows4s.runtime.instanceengine.WorkflowInstanceEngine
import workflows4s.runtime.registry.InMemoryWorkflowRegistry
import workflows4s.runtime.wakeup.SleepingKnockerUpper
import workflows4s.runtime.{InMemoryRuntime, InMemoryWorkflowInstance}

object TripSearchWorkflowSuite extends IOSuite with Checkers:
  test("should find a trip"): (engine, log) =>
    runStepTest[Either[UnexpectedSignal, BookingConfirmation]](engine, log)(
      bookTripTestCaseGen,
      (testCase, workflowInstance) =>
        workflowInstance
          .deliverSignal(
            TripSearchSignal.bookTrip,
            TripSearchSignal.BookTrip(testCase.unsafeSelection),
          ),
    )

  test("should fail with an error when starting a search without email"): (engine, log) =>
    runStateTestWith(engine, log)(
      missingEmailTestCaseGen,
    )

  test("should fail with an error when the search is incomplete"): (engine, log) =>
    runStateTestWith(engine, log)(
      incompleteTestCaseGen,
    )

  test("should fail with an error when the email cannot be sent"): (engine, log) =>
    runStateTest(engine, log)(
      alwaysSucceed.copy(
        mailSender = FailureRate.alwaysFailed,
      ),
      notSentEmailTestCaseGen,
    )

  test("should decline a booking when include any error"): (engine, log) =>
    runStepTest[Either[UnexpectedSignal, BookingConfirmation]](engine, log)(
      declinedBookingTestCaseGen,
      (testCase, workflowInstance) =>
        workflowInstance
          .deliverSignal(
            TripSearchSignal.bookTrip,
            TripSearchSignal.BookTrip(testCase.unsafeSelection),
          ),
    )

  private given Show[UnexpectedSignal] = Show.fromToString

  private val runStateTestWith
      : (WorkflowInstanceEngine, Log[IO]) => Gen[TestCase[Unit]] => IO[Expectations] =
    (engine, log) => testCaseGen => runStepTest[Unit](engine, log)(testCaseGen, (_, _) => IO.unit)

  private val runStateTest
      : (WorkflowInstanceEngine, Log[IO]) => (FailureSettings, Gen[TestCase[Unit]]) => IO[
        Expectations,
      ] =
    (engine, log) =>
      (failureSettings, testCaseGen) =>
        runStepTestWith[Unit](engine, log)(failureSettings, testCaseGen, (_, _) => IO.unit)

  private def runStepTest[A: Show]: (WorkflowInstanceEngine, Log[IO]) => (
      Gen[TestCase[A]],
      (TestCase[A], InMemoryWorkflowInstance[TripSearchContext.Ctx]) => IO[A],
  ) => IO[Expectations] =
    (engine, log) =>
      (testCaseGen, testee) => runStepTestWith[A](engine, log)(alwaysSucceed, testCaseGen, testee)

  private def runStepTestWith[A: Show]: (WorkflowInstanceEngine, Log[IO]) => (
      FailureSettings,
      Gen[TestCase[A]],
      (TestCase[A], InMemoryWorkflowInstance[TripSearchContext.Ctx]) => IO[A],
  ) => IO[Expectations] =
    (engine, log) =>
      (failureSettings, testCaseGen, testee) =>
        forall(testCaseGen): testCase =>
          testResources(engine, testCase.accommodations, testCase.flights, failureSettings).use:
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

  final private case class FailureSettings(
      accommodationsSearchAgent: FailureRate,
      bookingService: FailureRate,
      flightsSearchAgent: FailureRate,
      mailSender: FailureRate,
  )

  private lazy val alwaysSucceed =
    FailureSettings(
      accommodationsSearchAgent = FailureRate.alwaysSucceed,
      bookingService = FailureRate.alwaysSucceed,
      flightsSearchAgent = FailureRate.alwaysSucceed,
      mailSender = FailureRate.alwaysSucceed,
    )

  private def testResources(
      engine: WorkflowInstanceEngine,
      accommodations: Map[String, List[Accommodation]],
      flights: Map[String, List[Flight]],
      failureSettings: FailureSettings,
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
            given StructuredLogger[IO] = NoOpLogger[IO]
            tripSearchWorkflow = TripSearchWorkflow(
              accommodationsSearchAgent = FakeAccommodationsSearchAgent(
                accommodationsStateRef,
                failureSettings.accommodationsSearchAgent,
              ),
              flightsSearchAgent = FakeFlightsSearchAgent(
                flightsStateRef,
                failureSettings.flightsSearchAgent,
              ),
              mailWriterAgent = FakeMailWriterAgent(writtenMailsStateRef),
              mailSender = FakeMailSender(
                sentMailsStateRef,
                failureSettings.mailSender,
              ),
              bookingService = FakeBookingService(
                bookingsStateRef,
                failureSettings.bookingService,
              ),
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
      maybeSelection: Option[TripSelection],
      expectedState: TripSearchState,
      otherPossibleState: Option[TripSearchState],
      expectedWrittenMails: List[(List[Flight], List[Accommodation], TripRequest)],
      expectedSentMails: List[Email],
      expectedBooking: List[TripSelection],
      expectedResult: A,
  ) derives Eq,
        Show:
    @SuppressWarnings(Array("org.wartremover.warts.OptionPartial"))
    def unsafeSelection: TripSelection = maybeSelection.get

  private lazy val bookTripTestCaseGen =
    for
      request <- tripRequestGen()
      accommodations <- accommodationsGen
      flights <- flightsGen
      emailAddress = FakeMailWriterAgent.findEmailUnsafe(request.question)
      tripOptions = FakeMailWriterAgent.tripOptionsFrom(flights, accommodations)
      selection <- Gen
        .oneOf(tripOptions)
        .map: tripOption =>
          TripSelection(
            BookingId(request.requestId.value),
            tripOption,
          )
      confirmation = BookingConfirmation(
        accepted = true,
        bookingId = Some(BookingId(request.requestId.value)),
      )
    yield TestCase(
      request = request,
      accommodations = Map(request.question -> accommodations),
      flights = Map(request.question -> flights),
      maybeSelection = Some(selection),
      expectedState = TripSearchState.Booked(confirmation),
      otherPossibleState = None,
      expectedWrittenMails = List((flights, accommodations, request)),
      expectedSentMails = List(
        Email(
          messageId = Email.MessageId(request.requestId.value),
          recipient = emailAddress,
          subject = MailWriterAgent.emailSubjectFrom(request.requestId),
          body = FakeMailWriterAgent.emailBody,
        ),
      ),
      expectedBooking = List(selection),
      expectedResult = confirmation.asRight[UnexpectedSignal],
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
        maybeSelection = None,
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
      maybeSelection = None,
      expectedState = TripSearchState.Canceled(
        state = TripSearchState.Started(request),
        reason = TripSearchError.NotSettled,
      ),
      otherPossibleState = Some(
        TripSearchState.Canceled(
          state = TripSearchState.Found(request, flights, accommodations),
          reason = TripSearchError.NotSettled,
        ),
      ),
      expectedWrittenMails = List.empty,
      expectedSentMails = List.empty,
      expectedBooking = List.empty,
      expectedResult = (),
    )

  private lazy val notSentEmailTestCaseGen =
    for
      request <- tripRequestGen()
      accommodations <- accommodationsGen
      flights <- flightsGen
    yield TestCase(
      request = request,
      accommodations = Map(request.question -> accommodations),
      flights = Map(request.question -> flights),
      maybeSelection = None,
      expectedState = TripSearchState.Canceled(
        state = TripSearchState.Found(request, flights, accommodations),
        reason = TripSearchError.NotSettled,
      ),
      otherPossibleState = None,
      expectedWrittenMails = List((flights, accommodations, request)),
      expectedSentMails = List.empty,
      expectedBooking = List.empty,
      expectedResult = (),
    )

  private lazy val declinedBookingTestCaseGen =
    for
      request <- tripRequestGen()
      accommodations <- accommodationsGen
      flights <- flightsGen
      emailAddress = FakeMailWriterAgent.findEmailUnsafe(request.question)
      tripOptions = FakeMailWriterAgent.tripOptionsFrom(flights, accommodations)
      selection <-
        tripOptionGen
          .retryUntil(x => !tripOptions.exists(_ === x))
          .map: tripOption =>
            TripSelection(
              BookingId(request.requestId.value),
              tripOption,
            )
    yield TestCase(
      request = request,
      accommodations = Map(request.question -> accommodations),
      flights = Map(request.question -> flights),
      maybeSelection = Some(selection),
      expectedState = TripSearchState.Booked(
        BookingConfirmation.notBooked,
      ),
      otherPossibleState = None,
      expectedWrittenMails = List((flights, accommodations, request)),
      expectedSentMails = List(
        Email(
          messageId = Email.MessageId(request.requestId.value),
          recipient = emailAddress,
          subject = MailWriterAgent.emailSubjectFrom(request.requestId),
          body = FakeMailWriterAgent.emailBody,
        ),
      ),
      expectedBooking = List.empty,
      expectedResult = BookingConfirmation.notBooked
        .asRight[UnexpectedSignal],
    )
