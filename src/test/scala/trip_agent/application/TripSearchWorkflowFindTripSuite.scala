package es.eriktorr
package trip_agent.application

import trip_agent.application.TripSearchWorkflow.{TripSearchError, TripSearchState}
import trip_agent.domain.TripSearchGenerators.{accommodationsGen, flightsGen, tripRequestGen}
import trip_agent.spec.refined.Types.FailureRate

import org.scalacheck.Gen

object TripSearchWorkflowFindTripSuite extends TripSearchWorkflowSuiteBase:
  test("should fail with an error when the search is incomplete"): (engine, log) =>
    runStateTest(engine, log)(
      incompleteTestCaseGen,
    )

  test("should fail with an error when any of the trip finder agents fail"): (engine, log) =>
    runStateTest(engine, log)(
      tripCannotBeFoundTestCaseGen,
    )

  private def incompleteTestCaseGen =
    for
      request <- tripRequestGen()
      (accommodations, flights) <- Gen.frequency(
        1 -> accommodationsGen.map(_ -> List.empty),
        1 -> flightsGen.map(List.empty -> _),
        1 -> Gen.const(List.empty -> List.empty),
      )
    yield TestCase(
      failureSettings = alwaysSucceed,
      request = request,
      accommodations = Map(request.question -> accommodations),
      flights = Map(request.question -> flights),
      sentMails = List.empty,
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

  private def tripCannotBeFoundTestCaseGen =
    for
      request <- tripRequestGen()
      accommodations <- accommodationsGen
      flights <- flightsGen
      (failureSettings, foundFlights, foundAccommodations) <- Gen.frequency(
        1 -> (
          alwaysSucceed.copy(
            accommodationsSearchAgent = FailureRate.alwaysFailed,
          ),
          flights,
          List.empty,
        ),
        1 -> (
          alwaysSucceed.copy(
            flightsSearchAgent = FailureRate.alwaysFailed,
          ),
          List.empty,
          accommodations,
        ),
      )
    yield TestCase(
      failureSettings = failureSettings,
      request = request,
      accommodations = Map(request.question -> accommodations),
      flights = Map(request.question -> flights),
      sentMails = List.empty,
      maybeSelection = None,
      expectedState = TripSearchState.Canceled(
        state = TripSearchState.Started(request),
        reason = TripSearchError.NotSettled,
      ),
      otherPossibleState = Some(
        TripSearchState.Canceled(
          state = TripSearchState.Found(request, foundFlights, foundAccommodations),
          reason = TripSearchError.NotSettled,
        ),
      ),
      expectedWrittenMails = List.empty,
      expectedSentMails = List.empty,
      expectedBooking = List.empty,
      expectedResult = (),
    )
