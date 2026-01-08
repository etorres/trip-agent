package es.eriktorr
package trip_agent.domain

import trip_agent.domain.TripSearchGenerators.questionGen

import weaver.SimpleIOSuite
import weaver.scalacheck.Checkers

object EmailSuite extends SimpleIOSuite with Checkers:
  test("should find email in a text"):
    forall(questionGen): question =>
      expect(Email.findEmail(question).isDefined)
