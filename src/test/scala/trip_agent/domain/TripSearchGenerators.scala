package es.eriktorr
package trip_agent.domain

import trip_agent.spec.EmailGenerators.emailAddressGen
import trip_agent.spec.StringGenerators.alphaNumericStringBetween
import trip_agent.spec.TemporalGenerators.zonedDateTimeGen

import cats.implicits.{catsSyntaxTuple2Semigroupal, catsSyntaxTuple6Semigroupal, toTraverseOps}
import io.hypersistence.tsid.TSID
import org.scalacheck.Gen
import org.scalacheck.cats.implicits.genInstances

import java.time.ZonedDateTime
import scala.util.Random

object TripSearchGenerators:
  private val accommodationIdGen = Gen.choose(1, Int.MaxValue)

  def accommodationGen(
      idGen: Gen[Int] = accommodationIdGen,
      nameGen: Gen[String] = alphaNumericStringBetween(3, 12),
      neighborhoodGen: Gen[String] = alphaNumericStringBetween(3, 12),
      checkinGen: Gen[ZonedDateTime] = zonedDateTimeGen,
      checkoutGen: Gen[ZonedDateTime] = zonedDateTimeGen,
      pricePerNightGen: Gen[Int] = Gen.choose(10, 100),
  ): Gen[Accommodation] =
    (
      idGen,
      nameGen,
      neighborhoodGen,
      checkinGen,
      checkoutGen,
      pricePerNightGen,
    ).mapN(Accommodation.apply)

  val accommodationsGen: Gen[List[Accommodation]] =
    for
      size <- Gen.choose(1, 3)
      ids <- Gen.containerOfN[Set, Int](size, accommodationIdGen)
      accommodations <- ids.toList.traverse: id =>
        accommodationGen(idGen = id)
    yield accommodations

  private val flightIdGen = Gen.choose(1, Int.MaxValue)

  def flightGen(
      idGen: Gen[Int] = flightIdGen,
      fromGen: Gen[String] = alphaNumericStringBetween(3, 12),
      toGen: Gen[String] = alphaNumericStringBetween(3, 12),
      departureGen: Gen[ZonedDateTime] = zonedDateTimeGen,
      arrivalGen: Gen[ZonedDateTime] = zonedDateTimeGen,
      priceGen: Gen[Int] = Gen.choose(10, 100),
  ): Gen[Flight] =
    (
      idGen,
      fromGen,
      toGen,
      departureGen,
      arrivalGen,
      priceGen,
    ).mapN(Flight.apply)

  val flightsGen: Gen[List[Flight]] =
    for
      size <- Gen.choose(1, 3)
      ids <- Gen.containerOfN[Set, Int](size, flightIdGen)
      flights <- ids.toList.traverse:
        flightGen(_)
    yield flights

  private val wordGen = alphaNumericStringBetween(3, 12)

  private def lineGen(seedGen: Gen[String]) =
    for
      seed <- seedGen
      line <- Gen.frequency(
        1 -> seed,
        4 -> (for
          size <- Gen.choose(1, 4)
          words <- Gen.listOfN(size, wordGen)
        yield Random.shuffle(seed :: words).mkString(" ")),
      )
    yield line

  private def linesGen(seedGen: Gen[String]) =
    Gen.frequency(
      1 -> lineGen(seedGen).map: line =>
        List(line),
      4 -> (for
        seedLine <- lineGen(seedGen)
        size <- Gen.choose(1, 2)
        otherLines <- Gen.listOfN(size, Gen.oneOf(lineGen(seedGen), wordGen))
      yield Random.shuffle(seedLine :: otherLines)),
    )

  val requestIdGen: Gen[RequestId] =
    val tsid = TSID.Factory.getTsid256
    RequestId(tsid)

  val questionGen: Gen[String] =
    for
      emailAddress <- emailAddressGen()
      lines <- linesGen(emailAddress)
    yield lines.mkString("\n")

  def tripRequestGen(
      requestIdGen: Gen[RequestId] = requestIdGen,
      questionGen: Gen[String] = questionGen,
  ): Gen[TripRequest] =
    (
      requestIdGen,
      questionGen,
    ).mapN(TripRequest.apply)

  val tripOptionGen: Gen[TripOption] =
    (
      accommodationIdGen,
      flightIdGen,
    ).mapN(TripOption.apply)
