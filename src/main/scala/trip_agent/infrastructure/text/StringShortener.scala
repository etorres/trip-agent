package es.eriktorr
package trip_agent.infrastructure.text

object StringShortener:
  def shorten(text: String, maxLength: Int): String =
    require(maxLength >= 0, "maxLength must be non-negative")
    if text.length <= maxLength then text
    else if maxLength <= 3 then text.take(maxLength)
    else text.take(maxLength - 3) + "..."

  extension (self: String)
    def abbr: String =
      shorten(self, maxLength = 100)
