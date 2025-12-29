package es.eriktorr
package trip_agent

import cats.Show
import cats.derived.*
import com.comcast.ip4s.{Host, Port}

final case class TripSearchConfig(
    ollamaConfig: TripSearchConfig.OllamaConfig,
)

object TripSearchConfig:
  final case class OllamaConfig(
      host: Host,
      insecure: Boolean,
      model: OllamaConfig.OllamaModel,
      port: Port,
  ) derives Show:
    def baseUrl: String =
      val protocol = if insecure then "http" else "https"
      s"$protocol://$host:$port"

  object OllamaConfig:
    enum OllamaModel(val name: String) derives Show:
      case PHI3 extends OllamaModel("phi3:3.8b")
      case MISTRAL extends OllamaModel("mistral:7b")
