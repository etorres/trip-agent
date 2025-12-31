package es.eriktorr
package trip_agent.application

import weaver.SimpleIOSuite

import java.time.LocalDate

object AccommodationServiceSuite extends SimpleIOSuite:
  test("Should read accommodation availabilities from the classpath"):
    AccommodationService.impl
      .accommodationsBy(
        checkin = LocalDate.parse("2026-05-07"),
        checkout = LocalDate.parse("2026-05-14"),
      )
      .map: obtained =>
        expect(clue(obtained.length) == 20)
