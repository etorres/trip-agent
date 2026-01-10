package es.eriktorr
package trip_agent.application

import trip_agent.application.TripSearchWorkflow.{TripSearchError, TripSearchState}
import trip_agent.application.agents.{FakeMailWriterAgent, MailWriterAgent}
import trip_agent.domain.Email
import trip_agent.domain.TripSearchGenerators.{accommodationsGen, flightsGen, tripRequestGen}
import trip_agent.spec.refined.Types.FailureRate

object TripSearchWorkflowSendEmailSuite extends TripSearchWorkflowSuiteBase:
  test("should ignore duplicated emails"): (engine, log) =>
    runStateTest(engine, log)(
      duplicatedMessageIdTestCaseGen,
    )

  test("should fail with an error when the email cannot be sent"): (engine, log) =>
    runStateTest(engine, log)(
      notSentEmailTestCaseGen,
    )

  private def duplicatedMessageIdTestCaseGen =
    for
      request <- tripRequestGen()
      accommodations <- accommodationsGen
      flights <- flightsGen
      emailAddress = FakeMailWriterAgent.findEmailUnsafe(request.question)
      tripOptions = FakeMailWriterAgent.tripOptionsFrom(flights, accommodations)
      email = Email(
        messageId = Email.MessageId(request.requestId.value),
        recipient = emailAddress,
        subject = MailWriterAgent.emailSubjectFrom(request.requestId),
        body = FakeMailWriterAgent.emailBody,
      )
    yield TestCase(
      failureSettings = alwaysSucceed,
      request = request,
      accommodations = Map(request.question -> accommodations),
      flights = Map(request.question -> flights),
      sentMails = List(email),
      maybeSelection = None,
      expectedState = TripSearchState.Sent(
        recipient = emailAddress,
        options = tripOptions,
      ),
      otherPossibleState = None,
      expectedWrittenMails = List((flights, accommodations, request)),
      expectedSentMails = List(email),
      expectedBooking = List.empty,
      expectedResult = (),
    )

  private def notSentEmailTestCaseGen =
    for
      request <- tripRequestGen()
      accommodations <- accommodationsGen
      flights <- flightsGen
    yield TestCase(
      failureSettings = alwaysSucceed.copy(
        mailSender = FailureRate.alwaysFailed,
      ),
      request = request,
      accommodations = Map(request.question -> accommodations),
      flights = Map(request.question -> flights),
      sentMails = List.empty,
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
