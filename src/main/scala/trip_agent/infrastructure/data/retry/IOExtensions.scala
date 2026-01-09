package es.eriktorr
package trip_agent.infrastructure.data.retry

import trip_agent.infrastructure.data.error.ThrowableUtils

import cats.effect.IO
import cats.implicits.showInterpolator
import com.ibm.icu.text.RuleBasedNumberFormat
import org.typelevel.log4cats.StructuredLogger
import retry.*
import retry.RetryDetails.NextStep.{DelayAndRetry, GiveUp}
import retry.syntax.retryingOnErrors

import java.util.Locale
import scala.concurrent.duration.{DurationInt, FiniteDuration}

object IOExtensions:
  extension [A](self: IO[A])
    def retryOnAnyError(
        maxRetries: Int,
        threshold: FiniteDuration,
    )(using logger: StructuredLogger[IO]): IO[A] =
      retryWith(
        self,
        maxRetries,
        threshold,
        errorHandler = ResultHandler.retryOnAllErrors(logError),
      )

    @SuppressWarnings(Array("org.wartremover.warts.IsInstanceOf"))
    def retryOnError(
        maxRetries: Int,
        threshold: FiniteDuration,
        handled: Class[? <: Throwable],
    )(using logger: StructuredLogger[IO]): IO[A] =
      retryWith(
        self,
        maxRetries,
        threshold,
        errorHandler = (error: Throwable, details: RetryDetails) =>
          val rootCause = ThrowableUtils.rootCause(error)
          if handled.isInstance(rootCause) then
            logError(error, details) *> IO.pure(HandlerDecision.Continue)
          else IO.pure(HandlerDecision.Stop),
      )

  private def retryWith[A](
      operation: IO[A],
      maxRetries: Int,
      threshold: FiniteDuration,
      errorHandler: ErrorHandler[IO, A],
  ) =
    operation.retryingOnErrors(
      policy = RetryPolicies.limitRetriesByCumulativeDelay(
        threshold = threshold,
        policy = RetryPolicies.limitRetries[IO](maxRetries) join
          RetryPolicies.exponentialBackoff(10.seconds),
      ),
      errorHandler = errorHandler,
    )

  private def logError(
      error: Throwable,
      details: RetryDetails,
  )(using logger: StructuredLogger[IO]) =
    val retryCount = details.retriesSoFar
    details.nextStepIfUnsuccessful match
      case DelayAndRetry(nextDelay) =>
        logger.info(error)(show"Retrying operation for the ${toOrdinal(retryCount + 1)} time")
      case GiveUp =>
        logger.error(error)(show"Giving up after $retryCount retries")

  private def toOrdinal(i: Int) = formatter.format(i, "%spellout-ordinal")

  private lazy val formatter =
    RuleBasedNumberFormat(Locale.ENGLISH, RuleBasedNumberFormat.SPELLOUT)
