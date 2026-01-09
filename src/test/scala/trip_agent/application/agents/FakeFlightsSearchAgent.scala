package es.eriktorr
package trip_agent.application.agents

import trip_agent.application.agents.FakeFlightsSearchAgent.FlightsSearchAgentState
import trip_agent.domain.Flight
import trip_agent.spec.SimulatedFailure
import trip_agent.spec.refined.Types.FailureRate

import cats.effect.std.Random
import cats.effect.{IO, Ref}

final class FakeFlightsSearchAgent(
    stateRef: Ref[IO, FlightsSearchAgentState],
    failureRate: FailureRate = FailureRate.alwaysSucceed,
)(using random: Random[IO])
    extends FlightsSearchAgent:
  override def flightsFor(question: String): IO[List[Flight]] =
    random.nextDouble.flatMap: roll =>
      if roll < failureRate
      then IO.raiseError(SimulatedFailure)
      else stateRef.get.map(_.flights.getOrElse(question, List.empty))

object FakeFlightsSearchAgent:
  final case class FlightsSearchAgentState(
      flights: Map[String, List[Flight]],
  )

  object FlightsSearchAgentState:
    val empty: FlightsSearchAgentState =
      FlightsSearchAgentState(Map.empty)
