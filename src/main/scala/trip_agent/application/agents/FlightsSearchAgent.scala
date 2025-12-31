package es.eriktorr
package trip_agent.application.agents

import trip_agent.application.FlightService
import trip_agent.application.agents.tools.AvailabilityLoader.addToScope
import trip_agent.application.agents.tools.DateExtractor
import trip_agent.application.agents.tools.LangChain4jUtils.variablesFrom
import trip_agent.domain.Flight
import trip_agent.infrastructure.data.retry.IOExtensions.retryOnError

import cats.effect.IO
import cats.implicits.showInterpolator
import dev.langchain4j.agentic.{Agent, AgenticServices}
import dev.langchain4j.model.chat.ChatModel
import dev.langchain4j.service.{SystemMessage, UserMessage, V}
import io.circe.Decoder
import io.circe.parser.parse
import org.typelevel.log4cats.StructuredLogger

trait FlightsSearchAgent:
  def flightsFor(question: String): IO[List[Flight]]

object FlightsSearchAgent:
  @SuppressWarnings(Array("org.wartremover.warts.AsInstanceOf"))
  def impl(
      flightService: FlightService,
      chatModel: ChatModel,
      dateExtractor: DateExtractor,
  )(using logger: StructuredLogger[IO]): FlightsSearchAgent =
    (question: String) =>
      (for
        _ <- logger.info(show"Searching flights for: \"$question\"")
        (departure, arrival) <- dateExtractor.datesFrom(question)
        availabilities <- flightService.flightsBy(departure, arrival)
        flightsSearchAgent =
          AgenticServices
            .sequenceBuilder()
            .subAgents(
              addToScope(availabilities),
              AgenticServices
                .agentBuilder(classOf[FlightsSearchExpert])
                .chatModel(chatModel)
                .build(),
            )
            .outputKey("flights")
            .build()
        answer <-
          IO.blocking(
            flightsSearchAgent
              .invoke(
                variablesFrom("question" -> question),
              )
              .asInstanceOf[String],
          )
        flights <- IO.fromEither(parse(answer).flatMap(_.as[Flights]))
      yield flights.flights).retryOnError(
        handled = classOf[java.net.http.HttpTimeoutException],
      )

  private trait FlightsSearchExpert:
    @SystemMessage(fromResource = "flights_search/system_message.txt")
    @UserMessage(fromResource = "flights_search/user_message.txt")
    @Agent(
      description = "Searches for flights based on the given question",
      outputKey = "flights",
    )
    def findFlights(
        @V("question") question: String,
        @V("availabilities") availabilities: String,
    ): String

  final private case class Flights(
      flights: List[Flight],
  ) derives Decoder
