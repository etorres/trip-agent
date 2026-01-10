package es.eriktorr
package trip_agent.infrastructure.text

import trip_agent.infrastructure.text.StringShortener

import weaver.FunSuite

import scala.util.{Failure, Try}

object StringShortenerSuite extends FunSuite:
  test("should abbreviate a long text to a maximum length of 20 characters"):
    val text = "The quick brown fox jumps over the lazy dog."
    expect.eql(
      "The quick brown f...",
      StringShortener.shorten(text, 20),
    )

  test("should abbreviate a short text (no truncation needed)"):
    val text = "Hi everyone!"
    expect.eql(
      "Hi everyone!",
      StringShortener.shorten(text, 20),
    )

  test("should handle very short maximum length"):
    val text = "Hi!"
    expect.eql(
      text,
      StringShortener.shorten(text, 5),
    )

  test("fail with an error with negative maximum length"):
    matches(Try(StringShortener.shorten("Anything", -5))):
      case Failure(error) =>
        error match
          case e: IllegalArgumentException =>
            expect.eql("requirement failed: maxLength must be non-negative", e.getMessage)
          case other => failure(s"Unexpected error type: ${other.getClass.getName}")
