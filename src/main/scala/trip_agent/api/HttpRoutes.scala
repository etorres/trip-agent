package es.eriktorr
package trip_agent.api

import trip_agent.application.TripSearchWorkflow.{TripSearchSignal, TripSearchState}
import trip_agent.domain.*
import trip_agent.domain.TSIDCats.given
import trip_agent.infrastructure.TSIDGen

import cats.effect.IO
import cats.effect.std.Dispatcher
import cats.effect.unsafe.IORuntime
import cats.implicits.{catsSyntaxEq, showInterpolator}
import com.github.pjfanning.pekkohttpcirce.FailFastCirceSupport
import io.hypersistence.tsid.TSID
import org.apache.pekko.http.scaladsl.server.Directives.*
import org.apache.pekko.http.scaladsl.server.Route

import scala.util.Try

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
                        .findTrip(
                          requestId.value.toString,
                          TripSearchSignal.FindTrip(
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
      } ~
      path(
        "trip-searches" / Segment / "flight" / IntNumber / "accommodation" / IntNumber / "book",
      ) { (requestId, flightId, accommodationId) =>
        onSuccess((for
          tsid <- IO.fromTry(Try(TSID.from(requestId)))
          tripOption = TripOption(
            Flight.Id(flightId),
            Accommodation.Id(accommodationId),
          )
          state <- service.getState(requestId)
          confirmation <- state match
            case TripSearchState.Sent(_, options) =>
              IO.pure(options.exists(_ === tripOption))
                .ifM(
                  ifTrue = service.bookTrip(
                    requestId,
                    TripSearchSignal.BookTrip(
                      TripSelection(
                        bookingId = BookingId(tsid),
                        tripOption = tripOption,
                      ),
                    ),
                  ),
                  ifFalse = IO.raiseError(
                    IllegalArgumentException(
                      s"Trip option with flight $flightId and accommodation $accommodationId was not found in available options",
                    ),
                  ),
                )
            case other =>
              IO.raiseError(
                IllegalArgumentException(
                  s"Unexpected workflow state: ${other.getClass.getSimpleName}",
                ),
              )
        yield confirmation).unsafeToFuture()) { confirmation =>
          complete(confirmation)
        }
      }
