package es.eriktorr
package trip_agent.domain

import trip_agent.spec.EmailGenerators.emailAddressGen
import trip_agent.spec.StringGenerators.alphaNumericStringBetween

import cats.implicits.toTraverseOps
import org.scalacheck.Gen
import org.scalacheck.cats.implicits.genInstances

import scala.util.Random

object TripSearchGenerators:
  private val accommodationIdGen = alphaNumericStringBetween(3, 12)

  def accommodationGen(
      idGen: Gen[String] = accommodationIdGen,
  ): Gen[Accommodation] =
    idGen.map(Accommodation.apply)

  val accommodationsGen: Gen[List[Accommodation]] =
    for
      size <- Gen.choose(1, 3)
      ids <- Gen.containerOfN[Set, String](size, accommodationIdGen)
      accommodations <- ids.toList.traverse:
        accommodationGen(_)
    yield accommodations

  private val flightIdGen = alphaNumericStringBetween(3, 12)

  def flightGen(
      idGen: Gen[String] = flightIdGen,
  ): Gen[Flight] =
    idGen.map(Flight.apply)

  val flightsGen: Gen[List[Flight]] =
    for
      size <- Gen.choose(1, 3)
      ids <- Gen.containerOfN[Set, String](size, flightIdGen)
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

  val questionGen: Gen[String] =
    for
      emailAddress <- emailAddressGen()
      lines <- linesGen(emailAddress)
    yield lines.mkString("\n")
