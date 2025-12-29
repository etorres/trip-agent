package es.eriktorr
package trip_agent.domain

import trip_agent.domain.TripSearchGenerators.questionGen

import weaver.SimpleIOSuite
import weaver.scalacheck.Checkers

object TripSearchSuite extends SimpleIOSuite with Checkers:
  test("should find email in a text"):
    forall(questionGen): question =>
      expect(TripSearch.findEmail(question).isDefined)
