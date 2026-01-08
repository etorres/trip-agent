package es.eriktorr
package trip_agent.application

import trip_agent.application.AvailabilityService.Transformer.transform
import trip_agent.domain.{Accommodation, Flight}

import cats.arrow.Arrow
import cats.collections.Range
import cats.effect.IO
import fs2.Stream
import fs2.data.json.JsonException
import fs2.data.json.circe.given
import fs2.io.file.{Files, Path}
import io.circe.Decoder

import java.time.{LocalDate, LocalTime, ZonedDateTime, ZoneOffset}
import scala.reflect.ClassTag

object AvailabilityService:
  def availabilityFilteredBy[A: {ClassTag, Decoder}, B](
      resourcePath: String,
      range: Range[LocalDate],
      dateFilter: (A, Range[ZonedDateTime]) => Boolean,
  )(using transformer: AvailabilityService.Transformer[A, B]): IO[List[B]] =
    val clazz: Class[?] = summon[ClassTag[A]].runtimeClass
    val path = Path.fromNioPath(pathTo(resourcePath, clazz))
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
      .filter(dateFilter(_, withTime(range)))
      .map(_.transform)
      .compile
      .toList

  private def withTime(
      range: Range[LocalDate],
  ) =
    val (start, end) =
      Arrow[Function1].split(
        (_: LocalDate).atTime(LocalTime.MIN),
        (_: LocalDate).atTime(LocalTime.MAX),
      )(range.start -> range.end)
    Range(start, end).map(_.atZone(ZoneOffset.UTC))

  private def readJsonLines(
      input: Stream[IO, Byte],
  ) =
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
      resourcePath: String,
      clazz: Class[?],
  ) =
    java.nio.file.Paths.get(
      java.util.Objects
        .requireNonNull(
          clazz.getResource(resourcePath),
        )
        .toURI,
    )

  trait Transformer[A, B]:
    def transform(a: A): B

  object Transformer:
    extension [A, B](self: A)
      def transform(using transformer: Transformer[A, B]): B =
        transformer.transform(self)

  final case class AccommodationAvailability(
      id: Accommodation.Id,
      name: String,
      neighborhood: String,
      availableFrom: ZonedDateTime,
      availableUntil: ZonedDateTime,
      pricePerNight: Int,
  ) derives Decoder

  object AccommodationAvailability:
    given accommodationTransformer: Transformer[
      AccommodationAvailability,
      Accommodation,
    ] =
      (availability: AccommodationAvailability) =>
        Accommodation(
          id = availability.id,
          name = availability.name,
          neighborhood = availability.neighborhood,
          checkin = availability.availableFrom,
          checkout = availability.availableUntil,
          pricePerNight = availability.pricePerNight,
        )

  final case class FlightAvailability(
      id: Flight.Id,
      from: String,
      to: String,
      departure: ZonedDateTime,
      returnLeg: ZonedDateTime,
      price: Int,
  ) derives Decoder

  object FlightAvailability:
    given flightTransformer: Transformer[
      FlightAvailability,
      Flight,
    ] =
      (availability: FlightAvailability) =>
        Flight(
          id = availability.id,
          from = availability.from,
          to = availability.to,
          departure = availability.departure,
          arrival = availability.returnLeg,
          price = availability.price,
        )
