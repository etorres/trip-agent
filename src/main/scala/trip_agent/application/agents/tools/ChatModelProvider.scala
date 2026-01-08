package es.eriktorr
package trip_agent.application.agents.tools

import trip_agent.TripSearchConfig.OllamaConfig
import trip_agent.infrastructure.OllamaApiClient

import cats.effect.IO
import dev.langchain4j.model.chat.Capability.RESPONSE_FORMAT_JSON_SCHEMA
import dev.langchain4j.model.chat.ChatModel
import dev.langchain4j.model.ollama.OllamaChatModel

import java.time.Duration
import scala.jdk.CollectionConverters.given

final class ChatModelProvider(
    ollamaApiClient: OllamaApiClient,
    config: OllamaConfig,
):
  def chatModel(
      verbose: Boolean,
  ): IO[ChatModel] =
    val chatModelBuilder = ollamaChatModelBuilder(verbose)
    IO.pure(config.model.cloudEnabled)
      .ifM(
        ifTrue = IO
          .fromOption(config.apiKey)(
            IllegalArgumentException("OLLAMA API key is not set"),
          )
          .map: apiKey =>
            chatModelBuilder
              .customHeaders(
                Map(
                  "Authorization" -> s"Bearer ${apiKey.value}",
                ).asJava,
              )
              .build(),
        ifFalse = ollamaApiClient.initModel *>
          IO.pure(chatModelBuilder.build()),
      )

  private def ollamaChatModelBuilder(
      verbose: Boolean,
  ) =
    OllamaChatModel
      .builder()
      .baseUrl(config.baseUrl)
      .logRequests(verbose)
      .logResponses(verbose)
      .modelName(config.model.name)
      .returnThinking(false)
      .supportedCapabilities(RESPONSE_FORMAT_JSON_SCHEMA)
      .temperature(0.2d)
      .think(true)
      .timeout(Duration.ofMinutes(2L))
      .topK(20)
      .topP(0.9d)
