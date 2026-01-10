package es.eriktorr
package trip_agent.application

import trip_agent.application.TripSearchWorkflow.{TripSearchSignal, TripSearchState}
import trip_agent.application.agents.{FakeMailWriterAgent, MailWriterAgent}
import trip_agent.domain.TripSearchGenerators.{
  accommodationsGen,
  flightsGen,
  tripOptionGen,
  tripRequestGen,
}
import trip_agent.domain.{BookingConfirmation, BookingId, Email, TripSelection}
import trip_agent.spec.refined.Types.FailureRate

import cats.implicits.{catsStdShowForEither, catsSyntaxEitherId, catsSyntaxEq}
import org.scalacheck.Gen
import workflows4s.runtime.WorkflowInstance.UnexpectedSignal

object TripSearchWorkflowBookTripSuite extends TripSearchWorkflowSuiteBase:
  test("should book a trip"): (engine, log) =>
    runStepTest[Either[UnexpectedSignal, BookingConfirmation]](engine, log)(
      bookTripTestCaseGen,
      (testCase, workflowInstance) =>
        workflowInstance
          .deliverSignal(
            TripSearchSignal.bookTrip,
            TripSearchSignal.BookTrip(testCase.unsafeSelection),
          ),
    )

  test("should fail with an error when the booking cannot be completed"): (engine, log) =>
    runStepTest[Either[UnexpectedSignal, BookingConfirmation]](engine, log)(
      notBookedTestCaseGen,
      (testCase, workflowInstance) =>
        workflowInstance
          .deliverSignal(
            TripSearchSignal.bookTrip,
            TripSearchSignal.BookTrip(testCase.unsafeSelection),
          ),
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

  private def bookTripTestCaseGen =
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
      failureSettings = alwaysSucceed,
      request = request,
      accommodations = Map(request.question -> accommodations),
      flights = Map(request.question -> flights),
      sentMails = List.empty,
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

  private def notBookedTestCaseGen =
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
    yield TestCase(
      failureSettings = alwaysSucceed.copy(
        bookingService = FailureRate.alwaysFailed,
      ),
      request = request,
      accommodations = Map(request.question -> accommodations),
      flights = Map(request.question -> flights),
      sentMails = List.empty,
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

  private def declinedBookingTestCaseGen =
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
      failureSettings = alwaysSucceed,
      request = request,
      accommodations = Map(request.question -> accommodations),
      flights = Map(request.question -> flights),
      sentMails = List.empty,
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
