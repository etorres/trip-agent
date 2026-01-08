package es.eriktorr
package trip_agent.api

import trip_agent.application.TripSearchWorkflow
import trip_agent.domain.TSIDCats.given
import trip_agent.domain.{RequestId, TripRequest}
import trip_agent.infrastructure.TSIDGen

import cats.effect.IO
import cats.effect.std.Dispatcher
import cats.effect.unsafe.IORuntime
import cats.implicits.showInterpolator
import com.github.pjfanning.pekkohttpcirce.FailFastCirceSupport
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
      } ~
        post {
          entity(as[TripSearchRequest]) { case request @ TripSearchRequest.FindTrip(question) =>
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
                            TripRequest(
                              requestId = requestId,
                              question = request.question,
                            ),
                          ),
                        ),
                    )
                  yield requestId
                .unsafeToFuture(),
            ) { requestId =>
              complete(
                TripSearchResponse
                  .SearchStarted(
                    show"We are processing your request. We'll send you the response to your email in a minute. Your request id is: ${requestId.value}",
                  ),
              )
            }
          }
        }
    } ~
      path("trip-searches" / Segment) { requestId =>
        get {
          onSuccess(service.getState(requestId).unsafeToFuture()) { state =>
            complete(state)
          }
        }
      }

// TODO: add booking routes:
// get state from runtime and check the booking against the possible options
