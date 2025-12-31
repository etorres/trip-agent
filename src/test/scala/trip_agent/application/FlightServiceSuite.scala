package es.eriktorr
package trip_agent.application

import weaver.SimpleIOSuite

import java.time.LocalDate

object FlightServiceSuite extends SimpleIOSuite:
  test("Should read flight availabilities from the classpath"):
    FlightService.impl
      .flightsBy(
        departure = LocalDate.parse("2026-05-07"),
        arrival = LocalDate.parse("2026-05-14"),
      )
      .map: obtained =>
        expect(clue(obtained.length) == 30)
