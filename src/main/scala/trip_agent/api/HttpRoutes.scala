package es.eriktorr
package trip_agent.api

import trip_agent.application.TripSearchWorkflow
import trip_agent.domain.RequestId

import cats.effect.unsafe.IORuntime
import com.github.pjfanning.pekkohttpcirce.FailFastCirceSupport
import io.circe.Encoder
import io.hypersistence.tsid.TSID
import org.apache.pekko.http.scaladsl.server.Directives.*
import org.apache.pekko.http.scaladsl.server.Route

final class HttpRoutes(
    service: TripSearchWorkflowService,
)(using ioRuntime: IORuntime)
    extends FailFastCirceSupport:
  val routes: Route =
    path("trip-searches") {
      get {
        onSuccess(service.listWorkflows.unsafeToFuture()) { list =>
          complete(list)
        }
      }
    } ~
      path("trip-searches" / Segment) { id =>
        post {
          onSuccess(
            service
              .startWorkflow(
                id,
                TripSearchWorkflow.TripSearchSignal.FindTrip(
                  requestId = RequestId(TSID.from(id)),
                  question =
                    "Find a trip from Seoul to Tokyo and back, from 2026-05-07 to 2026-05-14. The flight price not higher than 300 total and the total accommodation for the week not higher than 600. Send the suggestion to 'noop@example.com'",
                ),
              )
              .unsafeToFuture(),
          ) {
            complete("OK")
          }
        } ~
          get {
            onSuccess(service.getState(id).unsafeToFuture()) { state =>
              complete(state)
            }
          }
      }
