package es.eriktorr
package trip_agent.application

import trip_agent.application.TripSearchWorkflow.TripSearchState
import trip_agent.domain.*
import trip_agent.spec.refined.Types.FailureRate

import cats.derived.*
import cats.{Eq, Show}

trait TripSearchTestFixtures:
  final protected case class FailureSettings(
      accommodationsSearchAgent: FailureRate,
      bookingService: FailureRate,
      flightsSearchAgent: FailureRate,
      mailSender: FailureRate,
  ) derives Show

  protected val alwaysSucceed: FailureSettings =
    FailureSettings(
      accommodationsSearchAgent = FailureRate.alwaysSucceed,
      bookingService = FailureRate.alwaysSucceed,
      flightsSearchAgent = FailureRate.alwaysSucceed,
      mailSender = FailureRate.alwaysSucceed,
    )

  final protected case class TestCase[A](
      failureSettings: FailureSettings,
      request: TripRequest,
      accommodations: Map[String, List[Accommodation]],
      flights: Map[String, List[Flight]],
      sentMails: List[Email],
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
