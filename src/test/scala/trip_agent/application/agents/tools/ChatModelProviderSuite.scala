package es.eriktorr
package trip_agent.application.agents.tools

import trip_agent.TestTripSearchConfig.TestOllamaConfig
import trip_agent.infrastructure.FakeOllamaApiClient

import cats.effect.IO
import weaver.SimpleIOSuite

object ChatModelProviderSuite extends SimpleIOSuite:
  test("should connect to Ollama cloud models using authenticated API access".ignore):
    for
      ollamaApiKey <- IO.fromOption(sys.env.get("OLLAMA_API_KEY"))(
        RuntimeException("OLLAMA_API_KEY environment variable is not set"),
      )
      config =
        TestOllamaConfig.deepSeekV3_2Cloud
          .withApiKey(ollamaApiKey)
      chatModelProvider = ChatModelProvider(
        ollamaApiClient = FakeOllamaApiClient(),
        config = config,
      )
      chatModel <- chatModelProvider.chatModel(verbose = true)
      response = chatModel.chat("Why is the sky blue?")
    yield expect(response.nonEmpty)
