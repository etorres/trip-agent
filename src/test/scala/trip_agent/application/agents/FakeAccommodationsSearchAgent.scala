package es.eriktorr
package trip_agent.application.agents

import trip_agent.application.agents.FakeAccommodationsSearchAgent.AccommodationsSearchAgentState
import trip_agent.domain.Accommodation
import trip_agent.spec.SimulatedFailure
import trip_agent.spec.refined.Types.FailureRate

import cats.effect.std.Random
import cats.effect.{IO, Ref}

final class FakeAccommodationsSearchAgent(
    stateRef: Ref[IO, AccommodationsSearchAgentState],
    failureRate: FailureRate = FailureRate.alwaysSucceed,
)(using random: Random[IO])
    extends AccommodationsSearchAgent:
  override def accommodationsFor(question: String): IO[List[Accommodation]] =
    random.nextDouble.flatMap: roll =>
      if roll < failureRate
      then IO.raiseError(SimulatedFailure)
      else stateRef.get.map(_.accommodations.getOrElse(question, List.empty))

object FakeAccommodationsSearchAgent:
  final case class AccommodationsSearchAgentState(
      accommodations: Map[String, List[Accommodation]],
  )

  object AccommodationsSearchAgentState:
    val empty: AccommodationsSearchAgentState =
      AccommodationsSearchAgentState(Map.empty)
