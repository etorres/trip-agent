package es.eriktorr
package trip_agent.application.agents.tools

import dev.langchain4j.agentic.AgenticServices
import dev.langchain4j.agentic.AgenticServices.AgenticScopeAction
import dev.langchain4j.agentic.scope.AgenticScope
import io.circe.Encoder
import io.circe.syntax.EncoderOps

object AvailabilityLoader:
  def addToScope[A: Encoder](availabilities: A): AgenticScopeAction =
    AgenticServices.agentAction((agenticScope: AgenticScope) =>
      agenticScope.writeState(
        "availabilities",
        availabilities.asJson.spaces4,
      ),
    )
