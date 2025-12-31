package es.eriktorr
package trip_agent

import trip_agent.TripSearchConfig.OllamaConfig
import trip_agent.TripSearchConfig.OllamaConfig.OllamaModel

import com.comcast.ip4s.{Host, Port}

object TestTripSearchConfig:
  @SuppressWarnings(Array("org.wartremover.warts.OptionPartial"))
  enum TestOllamaConfig(val config: OllamaConfig):
    case phi3LocalContainer
        extends TestOllamaConfig(
          OllamaConfig(
            host = Host.fromString(TestOllamaConfig.host).get,
            insecure = TestOllamaConfig.insecure,
            model = OllamaModel.PHI3,
            port = Port.fromInt(TestOllamaConfig.port).get,
          ),
        )
    case mistralLocalContainer
        extends TestOllamaConfig(
          OllamaConfig(
            host = Host.fromString(TestOllamaConfig.host).get,
            insecure = TestOllamaConfig.insecure,
            model = OllamaModel.MISTRAL,
            port = Port.fromInt(TestOllamaConfig.port).get,
          ),
        )
    case deepSeekR1LocalContainer
        extends TestOllamaConfig(
          OllamaConfig(
            host = Host.fromString(TestOllamaConfig.host).get,
            insecure = TestOllamaConfig.insecure,
            model = OllamaModel.DEEPSEEK_R1,
            port = Port.fromInt(TestOllamaConfig.port).get,
          ),
        )

  object TestOllamaConfig:
    final private val host = "localhost"
    final private val insecure = true
    final private val port = 11434
