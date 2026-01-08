package es.eriktorr
package trip_agent

import trip_agent.domain.Email
import trip_agent.infrastructure.network.IpArgument.given
import trip_agent.infrastructure.security.Secret

import cats.Show
import cats.derived.*
import cats.implicits.{
  catsSyntaxEither,
  catsSyntaxEq,
  catsSyntaxOption,
  catsSyntaxTuple2Semigroupal,
  catsSyntaxTuple4Semigroupal,
  catsSyntaxTuple5Semigroupal,
}
import com.comcast.ip4s.{host, port, Host, Port}
import com.monovore.decline.Opts
import org.http4s.Uri

final case class TripSearchConfig(
    baseUri: Uri,
    senderEmail: Email.Address,
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

    val baseUriOpts =
      Opts
        .env[String](
          name = "TRIP_AGENT_BASE_URI",
          help = "Set the base URI.",
        )
        .mapValidated: value =>
          Uri.fromString(value).leftMap(_.message).toValidatedNel

    val senderEmailOpts =
      Opts
        .env[String](
          name = "TRIP_AGENT_SENDER_EMAIL",
          help = "Set the sender email address.",
        )
        .mapValidated: value =>
          Email.Address
            .fromString(value)
            .leftMap(_.getMessage)
            .toValidatedNel

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
          .env[String](
            name = "TRIP_AGENT_OLLAMA_API_KEY",
            help = "Set Ollama API key.",
          )
          .map(Secret.apply[String])
          .orNone,
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
        baseUriOpts,
        senderEmailOpts,
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
      apiKey: Option[Secret[String]],
      host: Host,
      insecure: Boolean,
      model: OllamaConfig.OllamaModel,
      port: Port,
  ) derives Show:
    def baseUrl: String =
      val protocol = if insecure then "http" else "https"
      s"$protocol://$host:$port"

  object OllamaConfig:
    enum OllamaModel(
        val name: String,
        val cloudEnabled: Boolean,
    ) derives Show:
      case PHI3
          extends OllamaModel(
            name = "phi3:3.8b",
            cloudEnabled = false,
          )
      case DEEPSEEK_R1
          extends OllamaModel(
            name = "deepseek-r1:1.5b",
            cloudEnabled = false,
          )
      case DEEPSEEK_V3_1
          extends OllamaModel(
            name = "deepseek-v3.1:671b-cloud",
            cloudEnabled = true,
          )
      case DEEPSEEK_V3_2
          extends OllamaModel(
            name = "deepseek-v3.2:cloud",
            cloudEnabled = true,
          )
