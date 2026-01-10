package es.eriktorr
package trip_agent.application

import trip_agent.application.TripSearchWorkflow.{TripSearchError, TripSearchState}
import trip_agent.domain.Email
import trip_agent.domain.TripSearchGenerators.tripRequestGen
import trip_agent.spec.StringGenerators.alphaNumericStringBetween

import org.scalacheck.Gen

object TripSearchWorkflowStartSearchSuite extends TripSearchWorkflowSuiteBase:
  test("should fail with an error when starting a search without email"): (engine, log) =>
    runStateTest(engine, log)(
      missingEmailTestCaseGen,
    )

  private def missingEmailTestCaseGen =
    tripRequestGen(
      questionGen = alphaNumericStringBetween(3, 12)
        .retryUntil: question =>
          Email.findEmail(question).isEmpty,
    ).map: request =>
      TestCase(
        failureSettings = alwaysSucceed,
        request = request,
        accommodations = Map.empty,
        flights = Map.empty,
        sentMails = List.empty,
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
