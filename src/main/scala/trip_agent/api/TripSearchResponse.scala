package es.eriktorr
package trip_agent.api

import trip_agent.infrastructure.network.PekkoCirceSerializer

import cats.derived.*
import cats.{Eq, Show}
import io.circe.Codec

enum TripSearchResponse derives Codec.AsObject, Eq, Show:
  case SearchStarted(response: String)

object TripSearchResponse:
  final class PekkoSerializer extends PekkoCirceSerializer[TripSearchResponse]:
    override def identifier: Int = 4725
