package es.eriktorr
package trip_agent.application.agents

import trip_agent.application.AccommodationService
import trip_agent.application.agents.tools.AvailabilityLoader.addToScope
import trip_agent.application.agents.tools.DateExtractor
import trip_agent.domain.Accommodation
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

trait AccommodationsSearchAgent:
  def accommodationsFor(question: String): IO[List[Accommodation]]

object AccommodationsSearchAgent:
  @transient
  private lazy val unsafeLogger: Logger =
    LoggerFactory.getLogger(classOf[AccommodationsSearchAgent])

  @SuppressWarnings(Array("org.wartremover.warts.AsInstanceOf"))
  def impl(
      accommodationService: AccommodationService,
      chatModel: ChatModel,
      dateExtractor: DateExtractor,
      verbose: Boolean,
  )(using logger: StructuredLogger[IO]): AccommodationsSearchAgent =
    (question: String) =>
      (for
        _ <- logger.info(show"Searching accommodations for: \"$question\"")
        (checkin, checkout) <- dateExtractor.datesFrom(question)
        availabilities <- accommodationService.accommodationsBy(checkin, checkout)
        accommodationsSearchAgent =
          AgenticServices
            .sequenceBuilder()
            .subAgents(
              addToScope(availabilities),
              AgenticServices
                .agentBuilder(classOf[AccommodationsSearchExpert])
                .chatModel(chatModel)
                .listener(
                  new AgentListener:
                    override def beforeAgentInvocation(agentRequest: AgentRequest): Unit =
                      if verbose then
                        val question = agentRequest.inputs().get("question")
                        val availabilities = agentRequest.inputs().get("availabilities")
                        unsafeLogger.info(s"""Before searching accommodations:
                                             |>> Question:
                                             |$question
                                             |>> Availabilities:
                                             |$availabilities""".stripMargin)
                    override def afterAgentInvocation(agentResponse: AgentResponse): Unit =
                      if verbose then
                        val accommodations = agentResponse.output()
                        unsafeLogger.info(s"""After searching accommodations:
                                             |$accommodations""".stripMargin),
                )
                .build(),
            )
            .outputKey("accommodations")
            .build()
        answer <-
          IO.blocking(
            accommodationsSearchAgent
              .invoke(
                variablesFrom("question" -> question),
              )
              .asInstanceOf[String],
          ).map(stripCodeFences)
        accommodations <- accommodationsFrom(answer)
      yield accommodations).retryOnAnyError(
        maxRetries = 3,
        threshold = 2.minutes,
      )

  private def accommodationsFrom(answer: String) =
    IO.fromEither:
      parse(answer).flatMap: json =>
        json
          .as[Accommodations]
          .map(_.accommodations)
          .orElse(json.as[List[Accommodation]])

  private trait AccommodationsSearchExpert:
    @SystemMessage(fromResource = "accommodations_search/system_message.txt")
    @UserMessage(fromResource = "accommodations_search/user_message.txt")
    @Agent(
      description = "Searches for accommodations based on the given question",
      outputKey = "accommodations",
    )
    def findAccommodations(
        @V("question") question: String,
        @V("availabilities") availabilities: String,
    ): String

  final private case class Accommodations(
      accommodations: List[Accommodation],
  ) derives Decoder
