package es.eriktorr
package trip_agent.application.agents

import trip_agent.application.FlightService
import trip_agent.application.agents.tools.AvailabilityLoader.addToScope
import trip_agent.application.agents.tools.DateExtractor
import trip_agent.domain.Flight
import trip_agent.infrastructure.data.retry.IOExtensions.retryOnAnyError
import trip_agent.infrastructure.text.MarkdownCleaner.stripCodeFences
import trip_agent.infrastructure.text.TemplateVariables.variablesFrom

import cats.effect.IO
import cats.implicits.showInterpolator
import dev.langchain4j.agentic.observability.{AgentListener, AgentRequest, AgentResponse}
import dev.langchain4j.agentic.{Agent, AgenticServices}
import dev.langchain4j.model.chat.ChatModel
import dev.langchain4j.service.{SystemMessage, UserMessage, V}
import io.circe.Decoder
import io.circe.parser.parse
import org.slf4j.{Logger, LoggerFactory}
import org.typelevel.log4cats.StructuredLogger

import scala.concurrent.duration.DurationInt

trait FlightsSearchAgent:
  def flightsFor(question: String): IO[List[Flight]]

object FlightsSearchAgent:
  @transient
  private lazy val unsafeLogger: Logger =
    LoggerFactory.getLogger(classOf[FlightsSearchAgent])

  @SuppressWarnings(Array("org.wartremover.warts.AsInstanceOf"))
  def impl(
      flightService: FlightService,
      chatModel: ChatModel,
      dateExtractor: DateExtractor,
      verbose: Boolean,
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
                .listener(
                  new AgentListener:
                    override def beforeAgentInvocation(agentRequest: AgentRequest): Unit =
                      if verbose then
                        val question = agentRequest.inputs().get("question")
                        val availabilities = agentRequest.inputs().get("availabilities")
                        unsafeLogger.info(s"""Before searching flights:
                                             |>> Question:
                                             |$question
                                             |>> Availabilities:
                                             |$availabilities""".stripMargin)
                    override def afterAgentInvocation(agentResponse: AgentResponse): Unit =
                      if verbose then
                        val flights = agentResponse.output()
                        unsafeLogger.info(s"""After searching flights:
                                             |$flights""".stripMargin),
                )
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
          ).map(stripCodeFences)
        flights <- flightsFrom(answer)
      yield flights).retryOnAnyError(
        maxRetries = 3,
        threshold = 2.minutes,
      )

  private def flightsFrom(answer: String) =
    IO.fromEither:
      parse(answer).flatMap: json =>
        json
          .as[Flights]
          .map(_.flights)
          .orElse(json.as[List[Flight]])

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
