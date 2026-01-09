package es.eriktorr
package trip_agent.infrastructure

import trip_agent.TripSearchConfig.OllamaConfig.OllamaModel
import trip_agent.infrastructure.OllamaApiClient.ApiError.{GenerateFailed, PullFailed}
import trip_agent.spec.refined.Types.FailureRate

import cats.effect.std.Random
import cats.effect.{IO, Resource}
import cats.implicits.catsSyntaxApplicativeErrorId

final class FakeOllamaApiClient(
    model: OllamaModel = OllamaModel.DEEPSEEK_R1,
    failureRate: FailureRate = FailureRate.alwaysSucceed,
)(using random: Random[IO])
    extends OllamaApiClient:
  override def initModel: IO[Unit] =
    random.nextDouble.flatMap: roll =>
      if roll < failureRate
      then
        random
          .elementOf(List(GenerateFailed(model), PullFailed(model)))
          .flatMap(_.raiseError)
      else IO.unit

object FakeOllamaApiClient:
  def resource()(using random: Random[IO]): Resource[IO, FakeOllamaApiClient] =
    Resource.pure[IO, FakeOllamaApiClient](FakeOllamaApiClient())
