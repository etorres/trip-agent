package es.eriktorr
package trip_agent.application

import cats.effect.IO
import fs2.Stream
import fs2.data.json.JsonException
import fs2.data.json.circe.given
import fs2.io.file.{Files, Path}
import io.circe.Decoder

import scala.reflect.ClassTag

trait AvailabilityService[A]:
  def loadAvailabilities: IO[List[A]]

object AvailabilityService:
  def accommodations: AvailabilityService[AccommodationAvailability] =
    impl[AccommodationAvailability]("/accommodation_availabilities.jsonl")

  def flights: AvailabilityService[FlightAvailability] =
    impl[FlightAvailability]("/flight_availabilities.jsonl")

  private def impl[A: {ClassTag, Decoder}](resource: String): AvailabilityService[A] =
    new AvailabilityService[A]:
      override def loadAvailabilities: IO[List[A]] =
        val clazz: Class[?] = summon[ClassTag[A]].runtimeClass
        val path = Path.fromNioPath(pathTo(resource, clazz))
        Files[IO]
          .readAll(path)
          .through(readJsonLines)
          .flatMap: json =>
            json.as[A] match
              case Right(value) => Stream.emit(value)
              case Left(error) =>
                Stream.raiseError[IO](
                  JsonException(
                    s"Failed to decode '${clazz.getCanonicalName}' from '$json'",
                    inner = error,
                  ),
                )
          .compile
          .toList

  private def readJsonLines(input: Stream[IO, Byte]) =
    input
      .through(fs2.text.utf8.decode)
      .through(fs2.text.lines)
      .flatMap: line =>
        Stream
          .emit(line)
          .covary[IO]
          .through(fs2.data.json.ast.parse)
          .handleErrorWith: error =>
            Stream.raiseError[IO](
              JsonException(s"'$line' is not a valid JSON value", inner = error),
            )

  private def pathTo(
      resource: String,
      clazz: Class[?],
  ) =
    java.nio.file.Paths.get(
      java.util.Objects
        .requireNonNull(
          clazz.getResource(resource),
        )
        .toURI,
    )
