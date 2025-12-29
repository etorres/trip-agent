package es.eriktorr
package trip_agent.infrastructure

import cats.Eq
import cats.derived.*
import cats.effect.IO
import io.circe.Codec
import io.circe.parser.parse
import org.typelevel.log4cats.slf4j.Slf4jLogger
import weaver.SimpleIOSuite

import scala.concurrent.duration.DurationInt

object LoggingSuite extends SimpleIOSuite:
  test("should write structured logs"):
    val targetPath = os.pwd / "target"
    val logsPath = targetPath / "logs" / "test.log"
    for
      _ <- IO
        .blocking(os.isDir(targetPath))
        .ifM(
          IO.unit,
          IO.raiseError(RuntimeException("target directory not found")),
        )
      _ <- IO.blocking(os.remove(logsPath)).void
      logger <- Slf4jLogger.fromName[IO]("test-logger")
      _ <- logger.info(Map("customField" -> "Custom content"))("Testing Slf4jLogger")
      _ <- IO
        .race(IO.sleep(10.seconds), IO.blocking(os.exists(logsPath)).iterateUntil(_ == true))
        .void
      rawJsonLog <- IO.blocking(os.read(logsPath))
      obtained <- IO.delay(parse(rawJsonLog).flatMap(_.as[TestLog]))
      expected = TestLog("Custom content", "INFO", "Testing Slf4jLogger")
    yield matches(obtained):
      case Right(value) => expect.eql(expected, value)

  final private case class TestLog(
      customField: String,
      `log.level`: String,
      message: String,
  ) derives Codec,
        Eq
