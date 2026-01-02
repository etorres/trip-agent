package es.eriktorr
package trip_agent.application.agents

import trip_agent.application.agents.tools.EmailExtractor
import trip_agent.application.agents.tools.LangChain4jUtils.variablesFrom
import trip_agent.domain.RequestId.given
import trip_agent.domain.{Accommodation, Flight, RequestId}

import cats.effect.IO
import cats.implicits.{showInterpolator, toShow}
import dev.langchain4j.agentic.observability.{AgentListener, AgentRequest, AgentResponse}
import dev.langchain4j.agentic.{Agent, AgenticServices}
import dev.langchain4j.model.chat.ChatModel
import dev.langchain4j.service.{SystemMessage, UserMessage, V}
import io.circe.syntax.EncoderOps
import org.slf4j.{Logger, LoggerFactory}
import org.typelevel.log4cats.StructuredLogger

trait MailWriterAgent:
  def writeEmail(
      accommodations: List[Accommodation],
      flights: List[Flight],
      question: String,
      requestId: RequestId,
  ): IO[(String, String)]

object MailWriterAgent:
  @transient
  private lazy val unsafeLogger: Logger =
    LoggerFactory.getLogger(classOf[MailWriterAgent])

  @SuppressWarnings(Array("org.wartremover.warts.AsInstanceOf"))
  def impl(
      chatModel: ChatModel,
      emailExtractor: EmailExtractor,
  )(using logger: StructuredLogger[IO]): MailWriterAgent =
    (
        accommodations: List[Accommodation],
        flights: List[Flight],
        question: String,
        requestId: RequestId,
    ) =>
      for
        _ <- logger.info(show"Writing email for: \"$question\"")
        recipientEmail <- emailExtractor.emailFrom(question)
        mailSenderAgent =
          AgenticServices
            .sequenceBuilder()
            .subAgents(
              AgenticServices
                .agentBuilder(classOf[MailWriter])
                .chatModel(chatModel)
                .listener(
                  new AgentListener:
                    override def beforeAgentInvocation(agentRequest: AgentRequest): Unit =
                      val requestId = agentRequest.inputs().get("requestId")
                      val recipientEmail = agentRequest.inputs().get("recipientEmail")
                      val accommodations = agentRequest.inputs().get("accommodations")
                      val flights = agentRequest.inputs().get("flights")
                      val question = agentRequest.inputs().get("question")
                      unsafeLogger.info(s"""Before writing email:
                                           |>> RequestId:
                                           |$requestId
                                           |>> RecipientEmail:
                                           |$recipientEmail
                                           |>> Accommodations:
                                           |$accommodations
                                           |>> flights:
                                           |$flights
                                           |>> Question:
                                           |$question""".stripMargin)
                    override def afterAgentInvocation(agentResponse: AgentResponse): Unit =
                      val emailBody = agentResponse.output()
                      unsafeLogger.info(s"""After writing email:
                                           |$emailBody""".stripMargin),
                )
                .build(),
            )
            .outputKey("emailBody")
            .build()
        emailBody <-
          IO.blocking(
            mailSenderAgent
              .invoke(
                variablesFrom(
                  "requestId" -> requestId.value.show,
                  "recipientEmail" -> recipientEmail,
                  "accommodations" -> accommodations.asJson.spaces4,
                  "flights" -> flights.asJson.spaces4,
                  "question" -> question,
                ),
              )
              .asInstanceOf[String],
          )
      yield recipientEmail -> emailBody

  private trait MailWriter:
    @SystemMessage(fromResource = "mail_writer/system_message.txt")
    @UserMessage(fromResource = "mail_writer/user_message.txt")
    @Agent(
      description =
        "Write a recommendation with the best value combination flight (outbound and return) and accommodation",
      outputKey = "emailBody",
    )
    def writeRecommendation(
        @V("requestId") requestId: String,
        @V("recipientEmail") recipientEmail: String,
        @V("flights") flights: String,
        @V("accommodations") accommodations: String,
        @V("question") question: String,
    ): String
