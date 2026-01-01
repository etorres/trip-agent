package es.eriktorr
package trip_agent.api

import trip_agent.application.TripSearchWorkflow
import trip_agent.application.TripSearchWorkflow.TripSearchSignal

import cats.effect.IO
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.persistence.jdbc.query.scaladsl.JdbcReadJournal
import org.apache.pekko.persistence.query.scaladsl.{CurrentPersistenceIdsQuery, ReadJournal}
import org.apache.pekko.stream.scaladsl.Sink
import workflows4s.runtime.WorkflowInstance.UnexpectedSignal
import workflows4s.runtime.pekko.PekkoRuntime

trait TripSearchWorkflowService:
  def startWorkflow(
      id: String,
      input: TripSearchWorkflow.TripSearchSignal.FindTrip,
  ): IO[Unit]
  def listWorkflows: IO[Seq[String]]
  def getState(id: String): IO[TripSearchWorkflow.TripSearchState]

object TripSearchWorkflowService:
  type Journal = ReadJournal & CurrentPersistenceIdsQuery

  def impl(
      journal: JdbcReadJournal,
      runtime: PekkoRuntime[TripSearchWorkflow.TripSearchContext.Ctx],
  )(using actorSystem: ActorSystem[Any]): TripSearchWorkflowService =
    new TripSearchWorkflowService:
      override def startWorkflow(
          id: String,
          input: TripSearchSignal.FindTrip,
      ): IO[Unit] =
        val workflow = runtime.createInstance_(id)
        IO.fromFuture(
          IO(workflow.deliverSignal(TripSearchWorkflow.TripSearchSignal.findTrip, input)),
        ).flatMap {
          case Left(UnexpectedSignal(signal)) =>
            IO.raiseError(RuntimeException(s"Unexpected creation signal $signal for instance $id"))
          case Right(response) => IO.pure(response)
        }

      override def listWorkflows: IO[Seq[String]] =
        IO.fromFuture(IO(journal.currentPersistenceIds().runWith(Sink.seq)))

      override def getState(id: String): IO[TripSearchWorkflow.TripSearchState] =
        val workflow = runtime.createInstance_(id)
        IO.fromFuture(IO(workflow.queryState()))
