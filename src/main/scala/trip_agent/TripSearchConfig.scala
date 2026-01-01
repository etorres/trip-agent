package es.eriktorr
package trip_agent

import trip_agent.infrastructure.network.IpArgument.given
import trip_agent.infrastructure.security.Secret

import cats.Show
import cats.derived.*
import cats.implicits.{
  catsSyntaxEq,
  catsSyntaxOption,
  catsSyntaxTuple2Semigroupal,
  catsSyntaxTuple4Semigroupal,
  catsSyntaxTuple5Semigroupal,
}
import com.comcast.ip4s.{host, port, Host, Port}
import com.monovore.decline.Opts

final case class TripSearchConfig(
    dbConfig: TripSearchConfig.DbConfig,
    ollamaConfig: TripSearchConfig.OllamaConfig,
)

object TripSearchConfig:
  def opts: Opts[TripSearchConfig] =
    val tsidNodeOpts =
      Opts
        .env[Long](
          name = "TSID_NODE",
          help = "Set the TSID node ID.",
        )

    val dbConfigOpts =
      (
        Opts
          .env[Host](
            name = "TRIP_AGENT_DB_HOST",
            help = "Set the database host.",
          )
          .withDefault(host"localhost"),
        Opts
          .env[Port](
            name = "TRIP_AGENT_DB_PORT",
            help = "Set the database port.",
          )
          .withDefault(port"5432"),
        Opts.env[String](
          name = "TRIP_AGENT_DB_DATABASE",
          help = "Set the database name.",
        ),
        Opts.env[String](
          name = "TRIP_AGENT_DB_USERNAME",
          help = "Set the database username.",
        ),
        Opts
          .env[String](
            name = "TRIP_AGENT_DB_PASSWORD",
            help = "Set the database password.",
          )
          .map(Secret.apply[String]),
      ).mapN(DbConfig.apply)

    val ollamaOpts =
      (
        Opts
          .env[Host](
            name = "TRIP_AGENT_OLLAMA_HOST",
            help = "Set Ollama hostname or IP address.",
          )
          .withDefault(host"localhost"),
        Opts
          .env[String](
            name = "TRIP_AGENT_OLLAMA_INSECURE",
            help = "Is Ollama configured for secure (https) or insecure (http) access?",
          )
          .mapValidated(
            _.toBooleanOption
              .toValidNel("Must be true or false"),
          )
          .withDefault(true),
        Opts
          .env[String](
            name = "TRIP_AGENT_OLLAMA_MODEL",
            help = "Set Ollama model.",
          )
          .mapValidated: value =>
            OllamaConfig.OllamaModel.values
              .find: model =>
                model.name === value
              .toValidNel(s"Unsupported Ollama model: $value"),
        Opts
          .env[Port](
            name = "TRIP_AGENT_OLLAMA_PORT",
            help = "Set Ollama port number.",
          )
          .withDefault(port"11434"),
      ).mapN(OllamaConfig.apply)

    (
      tsidNodeOpts,
      (
        dbConfigOpts,
        ollamaOpts,
      ).mapN(TripSearchConfig.apply),
    ).tupled.map(_._2)

  final case class DbConfig(
      host: Host,
      port: Port,
      database: String,
      username: String,
      password: Secret[String],
  ):
    def jbcUrl = s"jdbc:postgresql://$host:$port/$database"

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
      case DEEPSEEK_R1 extends OllamaModel("deepseek-r1:1.5b")
