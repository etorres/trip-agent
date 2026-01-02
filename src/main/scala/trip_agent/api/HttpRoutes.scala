package es.eriktorr
package trip_agent.api

import trip_agent.application.TripSearchWorkflow
import trip_agent.domain.RequestId
import trip_agent.domain.RequestId.given
import trip_agent.infrastructure.TSIDGen

import cats.effect.IO
import cats.effect.std.Dispatcher
import cats.effect.unsafe.IORuntime
import cats.implicits.showInterpolator
import com.github.pjfanning.pekkohttpcirce.FailFastCirceSupport
import io.circe.Encoder
import io.hypersistence.tsid.TSID
import org.apache.pekko.http.scaladsl.server.Directives.*
import org.apache.pekko.http.scaladsl.server.Route

final class HttpRoutes(
    service: TripSearchWorkflowService,
)(using ioRuntime: IORuntime, tsidGen: TSIDGen[IO])
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
            Dispatcher
              .sequential[IO]
              .use: dispatcher =>
                for
                  requestId <- tsidGen.randomTSID.map(RequestId.apply)
                  _ = dispatcher.unsafeRunAndForget(
                    service
                      .startWorkflow(
                        requestId.value.toString,
                        TripSearchWorkflow.TripSearchSignal.FindTrip(
                          requestId = requestId,
                          question =
                            "Find a trip from Seoul to Tokyo and back, from 2026-05-07 to 2026-05-14. The flight price not higher than 300 total and the total accommodation for the week not higher than 600. Send the suggestion to 'noop@example.com'",
                        ),
                      ),
                  )
                yield requestId
              .unsafeToFuture(),
          ) { requestId =>
            complete(
              TripSearchResponse
                .FindTrip(
                  show"We are processing your request. We'll send you the response to your email in a minute. Your request id is: ${requestId.value}",
                ),
            )
          }
        } ~
          get {
            onSuccess(service.getState(id).unsafeToFuture()) { state =>
              complete(state)
            }
          }
      }
