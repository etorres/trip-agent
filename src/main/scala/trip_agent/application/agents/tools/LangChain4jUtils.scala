package es.eriktorr
package trip_agent.application.agents.tools

import java.util.Map as JavaMap
import scala.jdk.CollectionConverters.given

object LangChain4jUtils:
  @SuppressWarnings(Array("org.wartremover.warts.AsInstanceOf"))
  def variablesFrom(
      first: (String, String),
      others: (String, String)*,
  ): JavaMap[String, AnyRef] =
    Map
      .from(first +: others)
      .asJava
      .asInstanceOf[JavaMap[String, AnyRef]]
