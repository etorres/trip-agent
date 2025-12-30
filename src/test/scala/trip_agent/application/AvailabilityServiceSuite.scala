package es.eriktorr
package trip_agent.application

import weaver.SimpleIOSuite

object AvailabilityServiceSuite extends SimpleIOSuite:
  test("Should read accommodation availabilities from the classpath"):
    val testee = AvailabilityService.accommodations
    testee.loadAvailabilities
      .map: obtained =>
        expect(obtained.length == 20)

  test("Should read flight availabilities from the classpath"):
    val testee = AvailabilityService.flights
    testee.loadAvailabilities
      .map: obtained =>
        expect(obtained.length == 40)
