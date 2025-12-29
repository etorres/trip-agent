package es.eriktorr
package trip_agent.application.agents

import trip_agent.application.agents.FakeFlightsSearchAgent.FlightsSearchAgentState
import trip_agent.domain.Flight

import cats.effect.{IO, Ref}

final class FakeFlightsSearchAgent(
    stateRef: Ref[IO, FlightsSearchAgentState],
) extends FlightsSearchAgent:
  override def flightsFor(question: String): IO[List[Flight]] =
    stateRef.get.map(_.flights.getOrElse(question, List.empty))

object FakeFlightsSearchAgent:
  final case class FlightsSearchAgentState(
      flights: Map[String, List[Flight]],
  )

  object FlightsSearchAgentState:
    val empty: FlightsSearchAgentState =
      FlightsSearchAgentState(Map.empty)
