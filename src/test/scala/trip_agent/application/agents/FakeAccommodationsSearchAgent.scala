package es.eriktorr
package trip_agent.application.agents

import trip_agent.application.agents.FakeAccommodationsSearchAgent.AccommodationsSearchAgentState
import trip_agent.domain.Accommodation

import cats.effect.{IO, Ref}

final class FakeAccommodationsSearchAgent(
    stateRef: Ref[IO, AccommodationsSearchAgentState],
) extends AccommodationsSearchAgent:
  override def accommodationsFor(question: String): IO[List[Accommodation]] =
    stateRef.get.map(_.accommodations.getOrElse(question, List.empty))

object FakeAccommodationsSearchAgent:
  final case class AccommodationsSearchAgentState(
      accommodations: Map[String, List[Accommodation]],
  )

  object AccommodationsSearchAgentState:
    val empty: AccommodationsSearchAgentState =
      AccommodationsSearchAgentState(Map.empty)
