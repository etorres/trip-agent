package es.eriktorr
package trip_agent.application.agents

import trip_agent.application.agents.tools.EmailExtractor
import trip_agent.application.agents.tools.LangChain4jUtils.variablesFrom
import trip_agent.domain.*
import trip_agent.domain.TSIDCats.given

import cats.Show
import cats.effect.IO
import cats.implicits.{showInterpolator, toShow}
import dev.langchain4j.agentic.observability.{AgentListener, AgentRequest, AgentResponse}
import dev.langchain4j.agentic.{Agent, AgenticServices}
import dev.langchain4j.model.chat.ChatModel
import dev.langchain4j.service.{SystemMessage, UserMessage, V}
import io.circe.syntax.EncoderOps
import io.circe.{Codec, Encoder}
import org.http4s.Uri
import org.slf4j.{Logger, LoggerFactory}
import org.typelevel.log4cats.StructuredLogger

trait MailWriterAgent:
  def writeEmail(
      flights: List[Flight],
      accommodations: List[Accommodation],
      request: TripRequest,
  ): IO[(Email, List[TripOption])]

object MailWriterAgent:
  @transient
  private lazy val unsafeLogger: Logger =
    LoggerFactory.getLogger(classOf[MailWriterAgent])

  @SuppressWarnings(Array("org.wartremover.warts.AsInstanceOf"))
  def impl(
      baseUri: Uri,
      chatModel: ChatModel,
      emailExtractor: EmailExtractor,
      verbose: Boolean,
  )(using logger: StructuredLogger[IO]): MailWriterAgent =
    (
        flights: List[Flight],
        accommodations: List[Accommodation],
        request: TripRequest,
    ) =>
      for
        _ <- logger.info(show"Writing email for: \"${request.question}\"")
        recipientEmail <- emailExtractor.emailFrom(request.question)
        tripOptions =
          (for
            flight <- flights
            accommodation <- accommodations
            tripOption = TripOption(
              flightId = flight.id,
              accommodationId = accommodation.id,
            )
          yield tripOption -> TripOptionDetails(
            bookingLinkFrom(baseUri, request.requestId, tripOption),
            accommodation,
            flight,
          )).toMap
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
                      if verbose then
                        val recipientEmail = agentRequest.inputs().get("recipientEmail")
                        val requestId = agentRequest.inputs().get("requestId")
                        val question = agentRequest.inputs().get("question")
                        val tripOptions = agentRequest.inputs().get("tripOptions")
                        unsafeLogger.info(s"""Before writing email:
                                             |>> RecipientEmail:
                                             |$recipientEmail
                                             |>> RequestId:
                                             |$requestId
                                             |>> Question:
                                             |$question
                                             |>> TripOptions:
                                             |$tripOptions""".stripMargin)
                    override def afterAgentInvocation(agentResponse: AgentResponse): Unit =
                      if verbose then
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
                  "recipientEmail" -> recipientEmail,
                  "requestId" -> request.requestId.value.show,
                  "question" -> request.question,
                  "tripOptions" -> tripOptions.values.asJson.spaces4,
                ),
              )
              .asInstanceOf[String],
          )
        email =
          Email(
            messageId = Email.MessageId(request.requestId.value),
            recipient = Email.Address.applyUnsafe(recipientEmail),
            subject = emailSubjectFrom(request.requestId),
            body = Email.Body.applyUnsafe(emailBody),
          )
      yield email -> tripOptions.keys.toList

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
        @V("tripOptions") tripOptions: String,
        @V("question") question: String,
    ): String

  private def bookingLinkFrom(
      baseUri: Uri,
      requestId: RequestId,
      tripOption: TripOption,
  ) =
    baseUri
      .addPath("trip-searches")
      .addPath(requestId.value.toString)
      .addPath("flight")
      .addPath(tripOption.flightId.toString)
      .addPath("accommodation")
      .addPath(tripOption.accommodationId.toString)
      .addPath("book")
      .renderString

  private case class TripOptionDetails(
      bookingLink: String,
      accommodation: Accommodation,
      flight: Flight,
  ) derives Codec

  def emailSubjectFrom(requestId: RequestId): Email.Subject =
    Email.Subject.applyUnsafe(
      show"Your trip is waiting for you! Request ID: ${requestId.value}",
    )
