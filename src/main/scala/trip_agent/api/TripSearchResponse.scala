package es.eriktorr
package trip_agent.api

import trip_agent.infrastructure.network.PekkoCirceSerializer

import io.circe.Codec

enum TripSearchResponse derives Codec:
  case FindTrip(response: String)

object TripSearchResponse:
  final class PekkoSerializer extends PekkoCirceSerializer[TripSearchResponse]:
    override def identifier: Int = 4725
