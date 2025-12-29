package es.eriktorr
package trip_agent.application.agents

import trip_agent.domain.Accommodation

import cats.effect.IO
import cats.implicits.showInterpolator

trait AccommodationsSearchAgent:
  def accommodationsFor(question: String): IO[List[Accommodation]]

object AccommodationsSearchAgent:
  def impl: AccommodationsSearchAgent =
    (question: String) =>
      IO.println(show"Searching accommodations for: $question")
        .map: _ =>
          List(Accommodation("acc_123")) // TODO
