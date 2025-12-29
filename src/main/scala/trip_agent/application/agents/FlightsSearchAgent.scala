package es.eriktorr
package trip_agent.application.agents

import trip_agent.domain.Flight

import cats.effect.IO
import cats.implicits.showInterpolator

trait FlightsSearchAgent:
  def flightsFor(question: String): IO[List[Flight]]

object FlightsSearchAgent:
  def impl: FlightsSearchAgent =
    (question: String) =>
      IO.println(show"Searching flights for: $question")
        .map: _ =>
          List(Flight("fli_456")) // TODO
