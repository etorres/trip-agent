package es.eriktorr
package trip_agent.api

import trip_agent.infrastructure.network.PekkoCirceSerializer

import cats.derived.*
import cats.{Eq, Show}
import io.circe.*

enum TripSearchRequest derives Codec.AsObject, Eq, Show:
  case FindTrip(question: String)

object TripSearchRequest:
  final class PekkoSerializer extends PekkoCirceSerializer[TripSearchRequest]:
    override def identifier: Int = 4549
