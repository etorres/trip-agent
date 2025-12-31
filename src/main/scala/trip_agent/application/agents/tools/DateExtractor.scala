package es.eriktorr
package trip_agent.application.agents.tools

import cats.arrow.Arrow
import cats.effect.IO
import cats.implicits.showInterpolator
import dev.langchain4j.agentic.Agent
import dev.langchain4j.model.chat.ChatModel
import dev.langchain4j.service.{AiServices, SystemMessage, UserMessage, V}
import io.circe.Decoder
import io.circe.parser.decode
import org.typelevel.log4cats.StructuredLogger

import java.time.LocalDate

trait DateExtractor:
  def datesFrom(question: String): IO[(LocalDate, LocalDate)]

object DateExtractor:
  @SuppressWarnings(Array("org.wartremover.warts.Throw"))
  def impl(
      chatModel: ChatModel,
  )(using logger: StructuredLogger[IO]): DateExtractor =
    (question: String) =>
      for
        _ <- logger.info(show"Extracting stay dates from: \"$question\"")
        assistant = AiServices.create(classOf[Assistant], chatModel)
        answer <- IO.blocking(assistant.findDates(question))
        stay = decode[Stay](answer).fold(throw _, identity)
        toLocalDate = (x: String) => LocalDate.parse(x)
      yield Arrow[Function1].split(
        toLocalDate,
        toLocalDate,
      )(stay.check_in -> stay.check_out)

  private trait Assistant:
    @SystemMessage(fromResource = "date_extractor/system_message.txt")
    @UserMessage(fromResource = "date_extractor/user_message.txt")
    @Agent(
      description =
        "Reads natural language requests about accommodation stays and returns only the inferred check-in and check-out dates as a minimal JSON object, normalized to ISO-8601 format",
      outputKey = "stay",
    )
    def findDates(
        @V("question") question: String,
    ): String

  final private case class Stay(
      check_in: String,
      check_out: String,
  ) derives Decoder
