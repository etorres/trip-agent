package es.eriktorr
package trip_agent.application.agents

import trip_agent.TestTripSearchConfig.TestOllamaConfig
import trip_agent.application.AvailabilityService
import trip_agent.application.agents.tools.ModelProvider
import trip_agent.infrastructure.FakeOllamaApiClient

import cats.effect.IO
import org.typelevel.log4cats.StructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import weaver.SimpleIOSuite

import java.time.ZonedDateTime

object AccommodationsSearchAgentSuite extends SimpleIOSuite:
  test("should find accommodations based on user-provided information"):
    for
      given StructuredLogger[IO] <- Slf4jLogger.create[IO]
      testee = AccommodationsSearchAgent.impl2(
        availabilityService = AvailabilityService.accommodations,
        modelProvider = ModelProvider(
          ollamaApiClient = FakeOllamaApiClient(),
          config = TestOllamaConfig.mistralLocalContainer.config,
        ),
      )
      obtained <- testee.accommodationsFor(
        "Find a trip from Seoul to Tokyo and back, from 2026-05-07 to 2026-05-14. The flight price not higher than 300 total and the total accommodation for the week not higher than 600. Send the suggestion to 'noop@example.com'",
      )
    yield
      val checkin = ZonedDateTime.parse("2026-05-07T00:00:00Z")
      val checkout = ZonedDateTime.parse("2026-05-14T23:59:59Z")
      expect(obtained.nonEmpty)
      &&
      forEach(obtained): accommodation =>
        expect(
          !accommodation.checkin.isBefore(checkin) &&
            !accommodation.checkout.isAfter(checkout),
        )
      &&
      expect(obtained.map(_.pricePerNight).sum <= 600)
