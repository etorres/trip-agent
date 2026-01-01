package es.eriktorr
package trip_agent.application.agents

import trip_agent.TestTripSearchConfig.TestOllamaConfig
import trip_agent.application.agents.tools.{ChatModelProvider, EmailExtractor}
import trip_agent.domain.{Accommodation, Flight, RequestId}
import trip_agent.infrastructure.{FakeOllamaApiClient, TSIDGen}

import cats.effect.IO
import cats.implicits.catsSyntaxApplicativeByName
import org.typelevel.log4cats.StructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import weaver.SimpleIOSuite

import java.time.ZonedDateTime

object MailWriterAgentSuite extends SimpleIOSuite:
  test("should send an email with trip findings".ignore):
    for
      onSbt <- IO(sys.env.contains("SBT_TEST_ENV_VARS"))
      _ <- ignore("not on sbt").unlessA(onSbt)
      given StructuredLogger[IO] <- Slf4jLogger.create[IO]
      chatModelProvider = ChatModelProvider(
        ollamaApiClient = FakeOllamaApiClient(),
        config = TestOllamaConfig.deepSeekR1LocalContainer.config,
      )
      chatModel <- chatModelProvider.chatModel(verbose = false)
      tsid <- TSIDGen[IO].randomTSID
      testee = MailWriterAgent.impl(
        chatModel = chatModel,
        emailExtractor = EmailExtractor.impl(chatModel),
      )
      (obtainedAddress, obtainedContent) <-
        testee.writeEmail(
          accommodations = List(
            Accommodation(
              id = 123,
              name = "Name",
              neighborhood = "Neighborhood",
              checkin = ZonedDateTime.now(),
              checkout = ZonedDateTime.now(),
              pricePerNight = 100,
            ),
          ),
          flights = List(
            Flight(
              id = 456,
              from = "From",
              to = "To",
              departure = ZonedDateTime.now(),
              arrival = ZonedDateTime.now(),
              price = 300,
            ),
          ),
          question =
            "Find a trip from Seoul to Tokyo and back, from 2026-05-07 to 2026-05-14. The flight price not higher than 300 total and the total accommodation for the week not higher than 600. Send the suggestion to 'noop@example.com'",
          requestId = RequestId(tsid),
        )
    yield expect.eql("noop@example.com", obtainedAddress)
      && expect(obtainedContent.nonEmpty)
