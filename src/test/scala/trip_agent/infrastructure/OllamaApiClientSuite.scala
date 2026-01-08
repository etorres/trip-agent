package es.eriktorr
package trip_agent.infrastructure

import trip_agent.TestTripSearchConfig.TestOllamaConfig
import trip_agent.infrastructure.HttpClient.httpClientWith

import cats.effect.{IO, Resource}
import org.typelevel.log4cats.slf4j.Slf4jLogger
import weaver.{Log, SimpleIOSuite}

import scala.concurrent.duration.{Duration, DurationInt}

object OllamaApiClientSuite extends SimpleIOSuite:
  loggedTest("should call the api with a model already available"): log =>
    testWith(
      log = log,
      testConfig = TestOllamaConfig.deepSeekR1LocalContainer,
      timeout = 30.seconds,
    )

  loggedTest("should download a model from the ollama library and load it into memory".ignore):
    log =>
      testWith(
        log = log,
        testConfig = TestOllamaConfig.phi3LocalContainer,
      )

  private def testWith(
      log: Log[IO],
      testConfig: TestOllamaConfig,
      timeout: Duration = OllamaApiClientSuite.timeout,
  ) = (for
    logger <- Resource.eval(Slf4jLogger.fromName[IO]("debug-logger"))
    httpClient <- httpClientWith(timeout, verbose)(using logger)
    apiClient = OllamaApiClient.impl(testConfig.config, httpClient)
  yield apiClient)
    .use(_.initModel)
    .flatMap(_ => log.info("successfully download the model"))
    .map(_ => success)

  private lazy val timeout = 2.minutes
  private lazy val verbose = true

  override def maxParallelism = 1
