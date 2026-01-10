package es.eriktorr
package trip_agent.infrastructure.text

import java.util.Map as JavaMap
import scala.jdk.CollectionConverters.given

object TemplateVariables:
  @SuppressWarnings(Array("org.wartremover.warts.AsInstanceOf"))
  def variablesFrom(
      first: (String, String),
      others: (String, String)*,
  ): JavaMap[String, AnyRef] =
    Map
      .from(first +: others)
      .asJava
      .asInstanceOf[JavaMap[String, AnyRef]]
