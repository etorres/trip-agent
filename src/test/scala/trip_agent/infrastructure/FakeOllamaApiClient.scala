package es.eriktorr
package trip_agent.infrastructure

import trip_agent.TripSearchConfig.OllamaConfig.OllamaModel
import trip_agent.infrastructure.OllamaApiClient.ApiError.{GenerateFailed, PullFailed}

import cats.effect.std.Random
import cats.effect.{IO, Resource}
import cats.implicits.catsSyntaxApplicativeErrorId

final class FakeOllamaApiClient(
    model: OllamaModel = OllamaModel.PHI3,
    successful: Boolean = true,
)(using random: Random[IO])
    extends OllamaApiClient:
  override def initModel: IO[Unit] =
    if successful then IO.unit
    else
      random
        .elementOf(List(GenerateFailed(model), PullFailed(model)))
        .flatMap(_.raiseError)

object FakeOllamaApiClient:
  def resource()(using random: Random[IO]): Resource[IO, FakeOllamaApiClient] =
    Resource.pure[IO, FakeOllamaApiClient](FakeOllamaApiClient())
