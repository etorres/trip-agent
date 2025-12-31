package es.eriktorr
package trip_agent.application.agents

import trip_agent.TestTripSearchConfig.TestOllamaConfig
import trip_agent.application.FlightService
import trip_agent.application.agents.tools.{ChatModelProvider, DateExtractor}
import trip_agent.infrastructure.FakeOllamaApiClient

import cats.effect.IO
import cats.implicits.catsSyntaxApplicativeByName
import org.typelevel.log4cats.StructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import weaver.SimpleIOSuite

import java.time.ZonedDateTime

object FlightsSearchAgentSuite extends SimpleIOSuite:
  test("should find flights based on user-provided information".ignore):
    for
      onSbt <- IO(sys.env.contains("SBT_TEST_ENV_VARS"))
      _ <- ignore("not on sbt").unlessA(onSbt)
      given StructuredLogger[IO] <- Slf4jLogger.create[IO]
      chatModelProvider = ChatModelProvider(
        ollamaApiClient = FakeOllamaApiClient(),
        config = TestOllamaConfig.deepSeekR1LocalContainer.config,
      )
      chatModel <- chatModelProvider.chatModel(verbose = false)
      testee = FlightsSearchAgent.impl(
        flightService = FlightService.impl,
        chatModel = chatModel,
        dateExtractor = DateExtractor.impl(chatModel),
      )
      obtained <- testee.flightsFor(
        "Find a trip from Seoul to Tokyo and back, from 2026-05-07 to 2026-05-14. The flight price not higher than 300 total and the total accommodation for the week not higher than 600. Send the suggestion to 'noop@example.com'",
      )
    yield
      val departure = ZonedDateTime.parse("2026-05-07T00:00:00Z")
      val arrival = ZonedDateTime.parse("2026-05-14T23:59:59Z")
      expect(obtained.nonEmpty)
      &&
      forEach(obtained): flight =>
        expect(
          clue(!flight.departure.isBefore(departure)) &&
            clue(!flight.arrival.isAfter(arrival)),
        )
      &&
      forEach(obtained): flight =>
        expect(clue(flight.price) <= 300)

  override def maxParallelism: Int = 1
