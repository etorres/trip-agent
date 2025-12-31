package es.eriktorr
package trip_agent.application.agents

import trip_agent.application.AccommodationService
import trip_agent.application.agents.tools.DateExtractor
import trip_agent.application.agents.tools.LangChain4jUtils.variablesFrom
import trip_agent.domain.Accommodation
import trip_agent.infrastructure.data.retry.IOExtensions.retryOnError

import cats.effect.IO
import cats.implicits.showInterpolator
import dev.langchain4j.agentic.AgenticServices.AgenticScopeAction
import dev.langchain4j.agentic.scope.AgenticScope
import dev.langchain4j.agentic.{Agent, AgenticServices}
import dev.langchain4j.model.chat.ChatModel
import dev.langchain4j.service.{SystemMessage, UserMessage, V}
import io.circe.Decoder
import io.circe.parser.parse
import io.circe.syntax.EncoderOps
import org.typelevel.log4cats.StructuredLogger

trait AccommodationsSearchAgent:
  def accommodationsFor(question: String): IO[List[Accommodation]]

object AccommodationsSearchAgent:
  @SuppressWarnings(Array("org.wartremover.warts.AsInstanceOf"))
  def impl(
      accommodationService: AccommodationService,
      chatModel: ChatModel,
      dateExtractor: DateExtractor,
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
              AgenticServices.agentAction(
                new AgenticScopeAction.NonThrowingConsumer[AgenticScope]:
                  override def accept(agenticScope: AgenticScope): Unit =
                    agenticScope.writeState("availabilities", availabilities.asJson.noSpaces),
              ),
              AgenticServices
                .agentBuilder(classOf[AccommodationsSearchExpert])
                .chatModel(chatModel)
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
          )
        accommodations <- IO.fromEither(parse(answer).flatMap(_.as[Accommodations]))
      yield accommodations.accommodations)
        .retryOnError(handled = classOf[java.net.http.HttpTimeoutException])

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
