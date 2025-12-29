package es.eriktorr
package trip_agent.infrastructure

import cats.effect.{IO, Resource}
import org.http4s.client.Client
import org.http4s.client.middleware.Logger as Http4sLogger
import org.http4s.ember.client.EmberClientBuilder
import org.typelevel.log4cats.Logger

import scala.concurrent.duration.Duration

object HttpClient:
  def httpClientWith(
      timeout: Duration = org.http4s.client.defaults.RequestTimeout,
      verbose: Boolean = false,
  )(using logger: Logger[IO]): Resource[IO, Client[IO]] =
    EmberClientBuilder
      .default[IO]
      .withTimeout(timeout)
      .build
      .map: httpClient =>
        if verbose then
          Http4sLogger[IO](
            logHeaders = true,
            logBody = true,
            logAction = Some(msg => logger.debug(msg)),
          )(httpClient)
        else httpClient
