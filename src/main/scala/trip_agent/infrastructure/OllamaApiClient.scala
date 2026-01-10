package es.eriktorr
package trip_agent.infrastructure

import trip_agent.TripSearchConfig.OllamaConfig
import trip_agent.infrastructure.data.error.HandledError

import cats.effect.IO
import cats.implicits.{catsSyntaxTuple5Semigroupal, showInterpolator}
import io.circe.syntax.given
import io.circe.{Decoder, Encoder, HCursor, Json}
import org.http4s.circe.CirceEntityCodec.given
import org.http4s.client.Client
import org.http4s.{Method, Request, Uri}

import java.time.Instant

trait OllamaApiClient:
  def initModel: IO[Unit]

object OllamaApiClient:
  def impl(
      config: OllamaConfig,
      httpClient: Client[IO],
  ): OllamaApiClient =
    new OllamaApiClient:
      override def initModel: IO[Unit] =
        pullModel *> loadModel

      private def pullModel =
        val request =
          Request[IO](Method.POST, Uri.unsafeFromString(s"${config.baseUrl}/api/pull"))
            .withEntity(
              PullRequest(
                insecure = Some(true),
                model = config.model.name,
                stream = Some(false),
              ).asJson,
            )
        httpClient
          .expect[PullResponse](request)
          .map(_.status == "success")
          .ifM(
            ifTrue = IO.unit,
            ifFalse = IO.raiseError(ApiError.PullFailed(config.model)),
          )

      private def loadModel =
        val request =
          Request[IO](Method.POST, Uri.unsafeFromString(s"${config.baseUrl}/api/generate"))
            .withEntity(
              GenerateRequest(
                keepAlive = Some("30m"),
                model = config.model.name,
                stream = Some(false),
              ).asJson,
            )
        httpClient
          .expect[GenerateResponse](request)
          .map: response =>
            response.done && response.doneReason == "load"
          .ifM(
            ifTrue = IO.unit,
            ifFalse = IO.raiseError(ApiError.GenerateFailed(config.model)),
          )

  final private case class PullRequest(
      insecure: Option[Boolean],
      model: String,
      stream: Option[Boolean],
  ) derives Encoder

  final private case class PullResponse(status: String) derives Decoder

  final private case class GenerateRequest(
      keepAlive: Option[String],
      model: String,
      stream: Option[Boolean],
  )

  private object GenerateRequest:
    given Encoder[GenerateRequest] =
      (request: GenerateRequest) =>
        Json.obj(
          ("keep_alive", Json.fromStringOrNull(request.keepAlive)),
          ("model", Json.fromString(request.model)),
          ("stream", Json.fromBooleanOrNull(request.stream)),
        )

  final private case class GenerateResponse(
      createdAt: Instant,
      done: Boolean,
      doneReason: String,
      model: String,
      response: String,
  )

  private object GenerateResponse:
    given Decoder[GenerateResponse] =
      (cursor: HCursor) =>
        (
          cursor.downField("created_at").as[Instant],
          cursor.downField("done").as[Boolean],
          cursor.downField("done_reason").as[String],
          cursor.downField("model").as[String],
          cursor.downField("response").as[String],
        ).mapN(GenerateResponse.apply)

  enum ApiError(val message: String) extends HandledError(message):
    case GenerateFailed(model: OllamaConfig.OllamaModel)
        extends ApiError(show"Failed to load the model $model into memory")
    case PullFailed(model: OllamaConfig.OllamaModel)
        extends ApiError(show"Failed to download the model $model from the ollama library")
