package es.eriktorr
package trip_agent

import trip_agent.TripSearchConfig.OllamaConfig
import trip_agent.TripSearchConfig.OllamaConfig.OllamaModel
import trip_agent.infrastructure.security.Secret

import com.comcast.ip4s.{Host, Port}

object TestTripSearchConfig:
  @SuppressWarnings(Array("org.wartremover.warts.OptionPartial"))
  enum TestOllamaConfig(val config: OllamaConfig):
    case phi3LocalContainer
        extends TestOllamaConfig(
          OllamaConfig(
            apiKey = None,
            host = Host.fromString(TestOllamaConfig.localHost).get,
            insecure = TestOllamaConfig.localInsecure,
            model = OllamaModel.PHI3,
            port = Port.fromInt(TestOllamaConfig.localPort).get,
          ),
        )
    case deepSeekR1LocalContainer
        extends TestOllamaConfig(
          OllamaConfig(
            apiKey = None,
            host = Host.fromString(TestOllamaConfig.localHost).get,
            insecure = TestOllamaConfig.localInsecure,
            model = OllamaModel.DEEPSEEK_R1,
            port = Port.fromInt(TestOllamaConfig.localPort).get,
          ),
        )
    case deepSeekV3_2Cloud
        extends TestOllamaConfig(
          OllamaConfig(
            apiKey = None,
            host = Host.fromString(TestOllamaConfig.cloudHost).get,
            insecure = TestOllamaConfig.cloudInsecure,
            model = OllamaModel.DEEPSEEK_V3_2,
            port = Port.fromInt(TestOllamaConfig.cloudPort).get,
          ),
        )

    def withApiKey(apiKey: String): OllamaConfig =
      config.copy(apiKey = Some(Secret(apiKey)))
  end TestOllamaConfig

  object TestOllamaConfig:
    final private val localHost = "localhost"
    final private val localInsecure = true
    final private val localPort = 11434

    final private val cloudHost = "ollama.com"
    final private val cloudInsecure = false
    final private val cloudPort = 443
