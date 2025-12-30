package es.eriktorr
package trip_agent.application.agents

import trip_agent.application.agents.tools.ModelProvider
import trip_agent.application.{AccommodationAvailability, AvailabilityService}
import trip_agent.domain.Accommodation
import trip_agent.infrastructure.data.retry.IOExtensions.retryOnError

import cats.effect.IO
import cats.implicits.showInterpolator
import dev.langchain4j.agentic.AgenticServices.AgenticScopeAction
import dev.langchain4j.agentic.scope.AgenticScope
import dev.langchain4j.agentic.{Agent, AgenticServices}
import dev.langchain4j.service.{UserMessage, V}
import io.circe.syntax.EncoderOps
import org.typelevel.log4cats.StructuredLogger
import io.circe.parser.parse

import java.time.ZonedDateTime
import scala.jdk.CollectionConverters.MapHasAsJava

trait AccommodationsSearchAgent:
  def accommodationsFor(question: String): IO[List[Accommodation]]

object AccommodationsSearchAgent:
  def impl: AccommodationsSearchAgent =
    (question: String) =>
      IO.println(show"Searching accommodations for: $question")
        .map: _ =>
          List(
            Accommodation(
              id = 123,
              name = "name",
              neighborhood = "neighborhood",
              checkin = ZonedDateTime.now(),
              checkout = ZonedDateTime.now(),
              pricePerNight = 123,
            ),
          ) // TODO

  @SuppressWarnings(Array("org.wartremover.warts.AsInstanceOf"))
  def impl2(
      availabilityService: AvailabilityService[AccommodationAvailability],
      modelProvider: ModelProvider,
      verbose: Boolean = false,
  )(using logger: StructuredLogger[IO]): AccommodationsSearchAgent =
    (question: String) =>
      (for
        _ <- IO.println(show"Searching accommodations for: $question") // TODO: logger
        availabilities <- availabilityService.loadAvailabilities
        chatModel <- modelProvider.chatModel(verbose)
        accommodationsSearchAgent =
          AgenticServices
            .sequenceBuilder()
            .subAgents(
              AgenticServices.agentAction(
                new AgenticScopeAction.NonThrowingConsumer[AgenticScope]:
                  override def accept(agenticScope: AgenticScope): Unit =
                    agenticScope.writeState("availabilities", availabilities.asJson.noSpaces),
              ),
              AgenticServices.agentAction(
                new AgenticScopeAction.NonThrowingConsumer[AgenticScope]:
                  override def accept(agenticScope: AgenticScope): Unit =
                    val availabilities = agenticScope.readState("availabilities")
                    println(s" >> HERE >> LOADED:\n$availabilities"), // TODO
              ),
              AgenticServices
                .agentBuilder(classOf[AccommodationsSearchExpert])
                .chatModel(chatModel)
                .build(),
              AgenticServices.agentAction(
                new AgenticScopeAction.NonThrowingConsumer[AgenticScope]:
                  override def accept(agenticScope: AgenticScope): Unit =
                    val accommodations = agenticScope.readState("accommodations")
                    println(s" >> HERE >> FOUND:\n$accommodations"), // TODO
              ),
            )
            .outputKey("accommodations")
            .build()
        response =
          accommodationsSearchAgent
            .invoke(
              Map
                .apply[String, AnyRef](
                  "checkin" -> "2026-05-07T15:00:00Z",
                  "checkout" -> "2026-05-14T11:00:00Z",
                  "question" -> question,
                )
                .asJava,
            )
            .asInstanceOf[String]
        _ <- IO.println(show" >> RESPONSE: $response") // TODO
        accommodations <- IO.fromEither(parse(response).flatMap(_.as[List[Accommodation]]))
      yield accommodations)
        .retryOnError(handled = classOf[java.net.http.HttpTimeoutException])

// TODO
//  private class AccommodationsLoader(
//      availabilities: List[AccommodationAvailability],
//  ):
//    @Agent(
//      description =
//        "Finds available accommodations for the given check-in and check-out dates. Format of the dates must be `2026-05-07T15:00:00Z`",
//      outputKey = "availabilities",
//    )
//    def loadAccommodationsFilteredBy(
//        @V("checkin") checkin: String,
//        @V("checkout") checkout: String,
//    ): String =
//      val checkinDate = ZonedDateTime.parse(checkin)
//      val checkoutDate = ZonedDateTime.parse(checkout)
//      availabilities
//        .filter: availability =>
//          !availability.availableFrom.isAfter(checkinDate) &&
//            !availability.availableUntil.isBefore(checkoutDate)
//        .map: availability =>
//          Accommodation(
//            id = availability.id,
//            name = availability.name,
//            neighborhood = availability.neighborhood,
//            checkin = availability.availableFrom,
//            checkout = availability.availableUntil,
//            pricePerNight = availability.pricePerNight,
//          )
//        .asJson
//        .noSpaces

  private trait AccommodationsSearchExpert:
    @UserMessage(fromResource = "accommodations_search_agent.txt")
    @Agent(
      description = "Searches for accommodations based on the given question",
      outputKey = "accommodations",
    )
    def findAccommodations(
        @V("question") question: String,
        @V("availabilities") availabilities: String,
    ): String
