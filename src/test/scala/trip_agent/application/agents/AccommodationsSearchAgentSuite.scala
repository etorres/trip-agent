package es.eriktorr
package trip_agent.application.agents

import trip_agent.TestTripSearchConfig.TestOllamaConfig
import trip_agent.application.AccommodationService
import trip_agent.application.agents.tools.{ChatModelProvider, DateExtractor}
import trip_agent.infrastructure.FakeOllamaApiClient

import cats.effect.IO
import cats.implicits.catsSyntaxApplicativeByName
import org.typelevel.log4cats.StructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import weaver.SimpleIOSuite

import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit.DAYS

object AccommodationsSearchAgentSuite extends SimpleIOSuite:
  test("should find accommodations based on user-provided information"):
    for
      onSbt <- IO(sys.env.contains("SBT_TEST_ENV_VARS"))
      _ <- ignore("not on sbt").unlessA(onSbt)
      given StructuredLogger[IO] <- Slf4jLogger.create[IO]
      chatModelProvider = ChatModelProvider(
        ollamaApiClient = FakeOllamaApiClient(),
        config = TestOllamaConfig.phi3LocalContainer.config,
      )
      chatModel <- chatModelProvider.chatModel(verbose = false)
      testee = AccommodationsSearchAgent.impl(
        accommodationService = AccommodationService.impl,
        chatModel = chatModel,
        dateExtractor = DateExtractor.impl(chatModel),
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
          clue(!accommodation.checkin.isBefore(checkin)) &&
            clue(!accommodation.checkout.isAfter(checkout)),
        )
      &&
      forEach(obtained): accommodation =>
        val stayInDays =
          DAYS
            .between(
              accommodation.checkin,
              accommodation.checkout,
            )
            .toInt
        val totalPrice =
          math.max(1, stayInDays) * accommodation.pricePerNight
        expect(clue(totalPrice) <= 600)

  override def maxParallelism: Int = 1
