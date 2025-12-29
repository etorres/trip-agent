package es.eriktorr
package trip_agent.spec

import trip_agent.spec.StringGenerators.alphaNumericStringBetween

import org.scalacheck.Gen

object EmailGenerators:
  private val domainGen: Gen[String] =
    Gen.oneOf("example.com", "example.net", "example.org")

  def emailAddressGen(
      domainGen: Gen[String] = domainGen,
  ): Gen[String] =
    for
      domain <- domainGen
      username <- alphaNumericStringBetween(3, 12)
    yield s"$username@$domain"
