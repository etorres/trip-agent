package es.eriktorr
package trip_agent.application.agents

import trip_agent.domain.{Accommodation, Flight}

import cats.effect.IO
import cats.implicits.showInterpolator
import dev.langchain4j.agent.tool.Tool
import dev.langchain4j.agentic.Agent
import dev.langchain4j.model.chat.ChatModel
import dev.langchain4j.service.{AiServices, SystemMessage, UserMessage, V}
import io.circe.syntax.EncoderOps
import org.typelevel.log4cats.StructuredLogger

trait MailSenderAgent:
  def sendEmail(
      accommodations: List[Accommodation],
      flights: List[Flight],
      question: String,
      requestId: String,
  ): IO[String]

object MailSenderAgent:
  def impl(
      chatModel: ChatModel,
  )(using logger: StructuredLogger[IO]): MailSenderAgent =
    (
        accommodations: List[Accommodation],
        flights: List[Flight],
        question: String,
        requestId: String,
    ) =>
      for
        _ <- logger.info(show"Sending email for: \"$requestId\"")
        assistant =
          AiServices
            .builder(classOf[Assistant])
            .chatModel(chatModel)
            .tools(MailSender())
            .build()
        answer <- IO.blocking(
          assistant.sendTripMail(
            accommodations = accommodations.asJson.spaces4,
            flights = flights.asJson.spaces4,
            question = question,
            requestId = requestId,
          ),
        )
      yield answer

  private class MailSender:
    @Tool(name = "send-mail")
    def sendEmail(
        from: String,
        to: String,
        subject: String,
        content: String,
    ): String =
      println(s"""From: $from,
                 |To: $to,
                 |Subject: $subject,
                 |$content""".stripMargin)
      to

  private trait Assistant:
    @SystemMessage(fromResource = "mail_sender/system_message.txt")
    @UserMessage(fromResource = "mail_sender/user_message.txt")
    @Agent(
      description =
        "Sends an email. By default, 'from' is 'trip.agency@example.com', 'subject' is the 'requestId' in scope",
      outputKey = "emailAddress",
    )
    def sendTripMail(
        @V("accommodations") accommodations: String,
        @V("flights") flights: String,
        @V("question") question: String,
        @V("requestId") requestId: String,
    ): String
