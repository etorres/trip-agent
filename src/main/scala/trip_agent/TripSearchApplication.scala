package es.eriktorr
package trip_agent

import trip_agent.application.agents.{
  AccommodationsSearchAgent,
  FlightsSearchAgent,
  MailSenderAgent,
}
import trip_agent.application.{BookingService, TripSearchWorkflow}
import trip_agent.infrastructure.TSIDGen

import cats.effect.{IO, IOApp}
import org.camunda.bpm.model.bpmn.Bpmn
import workflows4s.bpmn.BpmnRenderer
import workflows4s.runtime.InMemoryRuntime
import workflows4s.runtime.instanceengine.WorkflowInstanceEngine
import workflows4s.runtime.registry.InMemoryWorkflowRegistry
import workflows4s.runtime.wakeup.SleepingKnockerUpper

import java.io.File

object TripSearchApplication extends IOApp.Simple:
  override def run: IO[Unit] =
    (for
      knockerUpper <- SleepingKnockerUpper.create()
      registry <- InMemoryWorkflowRegistry().toResource
      engine = WorkflowInstanceEngine.default(knockerUpper, registry)
      tripSearchWorkflow = TripSearchWorkflow(
        accommodationsSearchAgent = AccommodationsSearchAgent.impl,
        flightsSearchAgent = FlightsSearchAgent.impl,
        mailSenderAgent = MailSenderAgent.impl,
        bookingService = BookingService.impl,
      )
      runtime <- InMemoryRuntime
        .default[TripSearchWorkflow.TripSearchContext.Ctx](
          workflow = tripSearchWorkflow.workflow,
          initialState = TripSearchWorkflow.TripSearchState.Empty,
          engine = engine,
        )
        .toResource
    yield runtime).use: runtime =>
      for
        tsid <- TSIDGen[IO].randomTSID
        workflowInstance <- runtime.createInstance(tsid.toString)
        _ <- workflowInstance.deliverSignal(
          TripSearchWorkflow.TripSearchSignal.findTrip,
          TripSearchWorkflow.TripSearchSignal.FindTrip("jane@example.org"),
        )
        _ <- workflowInstance.queryState().flatMap(IO.println)
        _ <- IO.blocking {
          val bpmnModel =
            BpmnRenderer.renderWorkflow(runtime.workflow.toProgress.toModel, "process")
          Bpmn.writeModelToFile(new File(s"pr.bpmn").getAbsoluteFile, bpmnModel)
        }
        _ <- workflowInstance.deliverSignal(
          TripSearchWorkflow.TripSearchSignal.bookTrip,
          TripSearchWorkflow.TripSearchSignal.BookTrip(approved = true),
        )
        _ <- workflowInstance.queryState().flatMap(IO.println)
        _ <- IO.println("Done!")
      yield ()
