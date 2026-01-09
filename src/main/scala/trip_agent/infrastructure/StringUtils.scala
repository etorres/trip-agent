package es.eriktorr
package trip_agent.infrastructure

object StringUtils:
  def snippet(text: String): String = shorten(text, maxLength = 100)

  def shorten(text: String, maxLength: Int): String =
    require(maxLength >= 0, "maxLength must be non-negative")
    if text.length <= maxLength then text
    else if maxLength <= 3 then text.take(maxLength)
    else text.take(maxLength - 3) + "..."

  extension (self: String) def abbr: String = snippet(self)
