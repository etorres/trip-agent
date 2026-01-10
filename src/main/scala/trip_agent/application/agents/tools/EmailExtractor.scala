package es.eriktorr
package trip_agent.application.agents.tools

import trip_agent.infrastructure.text.MarkdownCleaner.stripCodeFences

import cats.effect.IO
import cats.implicits.showInterpolator
import dev.langchain4j.agentic.Agent
import dev.langchain4j.model.chat.ChatModel
import dev.langchain4j.service.{AiServices, SystemMessage, UserMessage, V}
import io.circe.Decoder
import io.circe.parser.decode
import org.typelevel.log4cats.StructuredLogger

trait EmailExtractor:
  def emailFrom(question: String): IO[String]

object EmailExtractor:
  @SuppressWarnings(Array("org.wartremover.warts.Throw"))
  def impl(
      chatModel: ChatModel,
  )(using logger: StructuredLogger[IO]): EmailExtractor =
    (question: String) =>
      for
        _ <- logger.info(show"Extracting notification email from: \"$question\"")
        assistant = AiServices.create(classOf[Assistant], chatModel)
        answer <- IO
          .blocking(assistant.findEmailAddress(question))
          .map(stripCodeFences)
        contact = decode[Contact](answer).fold(throw _, identity)
      yield contact.notification_email

  private trait Assistant:
    @SystemMessage(fromResource = "email_extractor/system_message.txt")
    @UserMessage(fromResource = "email_extractor/user_message.txt")
    @Agent(
      description =
        "Reads natural language requests about accommodation stays and returns only the inferred email address as a minimal JSON object",
      outputKey = "contact",
    )
    def findEmailAddress(
        @V("question") question: String,
    ): String

  final private case class Contact(
      notification_email: String,
  ) derives Decoder
