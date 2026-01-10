package es.eriktorr
package trip_agent.application

import trip_agent.application.FakeBookingService.BookingServiceState
import trip_agent.application.FakeMailSender.MailSenderState
import trip_agent.application.TripSearchWorkflow.{
  TripSearchContext,
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
import trip_agent.domain.*
import trip_agent.domain.TSIDCats.given

import cats.Show
import cats.effect.{IO, Ref, Resource}
import cats.implicits.toShow
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

trait TripSearchWorkflowSuiteBase extends IOSuite with Checkers with TripSearchTestFixtures:
  override type Res = WorkflowInstanceEngine

  override def sharedResource: Resource[IO, Res] =
    for
      knockerUpper <- SleepingKnockerUpper.create()
      registry <- InMemoryWorkflowRegistry().toResource
      engine = WorkflowInstanceEngine.default(knockerUpper, registry)
    yield engine

  protected given Show[UnexpectedSignal] = Show.fromToString

  protected val runStateTest
      : (WorkflowInstanceEngine, Log[IO]) => Gen[TestCase[Unit]] => IO[Expectations] =
    (engine, log) => testCaseGen => runStepTest[Unit](engine, log)(testCaseGen, (_, _) => IO.unit)

  protected def runStepTest[A: Show]: (WorkflowInstanceEngine, Log[IO]) => (
      Gen[TestCase[A]],
      (TestCase[A], InMemoryWorkflowInstance[TripSearchContext.Ctx]) => IO[A],
  ) => IO[Expectations] =
    (engine, log) =>
      (testCaseGen, testee) =>
        forall(testCaseGen): testCase =>
          testResources(engine, testCase)
            .use: (runtime, writtenMailsStateRef, sentMailsStateRef, bookingsStateRef) =>
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

  private def testResources[A](
      engine: WorkflowInstanceEngine,
      testCase: TestCase[A],
  ) =
    for
      (tripSearchWorkflow, writtenMailsStateRef, sentMailsStateRef, bookingsStateRef) <-
        Resource.eval:
          for
            accommodationsStateRef <- Ref.of[IO, AccommodationsSearchAgentState](
              AccommodationsSearchAgentState.empty
                .copy(testCase.accommodations),
            )
            flightsStateRef <- Ref.of[IO, FlightsSearchAgentState](
              FlightsSearchAgentState.empty
                .copy(testCase.flights),
            )
            writtenMailsStateRef <- Ref.of[IO, MailWriterAgentState](
              MailWriterAgentState.empty,
            )
            sentMailsStateRef <- Ref.of[IO, MailSenderState](
              MailSenderState.empty
                .copy(testCase.sentMails),
            )
            bookingsStateRef <- Ref.of[IO, BookingServiceState](
              BookingServiceState.empty,
            )
            given StructuredLogger[IO] = NoOpLogger[IO]
            tripSearchWorkflow = TripSearchWorkflow(
              accommodationsSearchAgent = FakeAccommodationsSearchAgent(
                accommodationsStateRef,
                testCase.failureSettings.accommodationsSearchAgent,
              ),
              flightsSearchAgent = FakeFlightsSearchAgent(
                flightsStateRef,
                testCase.failureSettings.flightsSearchAgent,
              ),
              mailWriterAgent = FakeMailWriterAgent(writtenMailsStateRef),
              mailSender = FakeMailSender(
                sentMailsStateRef,
                testCase.failureSettings.mailSender,
              ),
              bookingService = FakeBookingService(
                bookingsStateRef,
                testCase.failureSettings.bookingService,
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
