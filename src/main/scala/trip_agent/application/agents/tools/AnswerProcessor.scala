package es.eriktorr
package trip_agent.application.agents.tools

object AnswerProcessor:
  def stripCodeFences(
      raw: String,
  ): String =
    val trimmed = raw.trim

    val withoutStart =
      if trimmed.startsWith("```")
      then trimmed.dropWhile(_ != '\n').drop(1)
      else trimmed

    val withoutEnd =
      if withoutStart.trim.endsWith("```") then
        withoutStart.reverse
          .dropWhile(_ != '\n')
          .drop(1)
          .reverse
      else withoutStart

    withoutEnd.trim
