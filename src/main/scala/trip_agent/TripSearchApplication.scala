package es.eriktorr
package trip_agent

import trip_agent.TripSearchConfig.OllamaConfig
import trip_agent.TripSearchConfig.OllamaConfig.OllamaModel
import trip_agent.application.*
import trip_agent.application.agents.tools.{ChatModelProvider, DateExtractor, EmailExtractor}
import trip_agent.application.agents.{
  AccommodationsSearchAgent,
  FlightsSearchAgent,
  MailWriterAgent,
}
import trip_agent.domain.RequestId
import trip_agent.infrastructure.HttpClient.httpClientWith
import trip_agent.infrastructure.{OllamaApiClient, TSIDGen}

import cats.effect.{ExitCode, IO, Resource}
import com.comcast.ip4s.{host, port}
import com.monovore.decline.Opts
import com.monovore.decline.effect.CommandIOApp
import org.camunda.bpm.model.bpmn.Bpmn
import org.typelevel.log4cats.StructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import workflows4s.bpmn.BpmnRenderer
import workflows4s.runtime.InMemoryRuntime
import workflows4s.runtime.instanceengine.WorkflowInstanceEngine
import workflows4s.runtime.registry.InMemoryWorkflowRegistry
import workflows4s.runtime.wakeup.SleepingKnockerUpper

import java.io.File

object TripSearchApplication
    extends CommandIOApp(name = "trip-search", header = "Trip Search Agent"):
  override def main: Opts[IO[ExitCode]] =
    Opts(
      (for
        knockerUpper <- SleepingKnockerUpper.create()
        registry <- InMemoryWorkflowRegistry().toResource
        engine = WorkflowInstanceEngine.default(knockerUpper, registry)
        given StructuredLogger[IO] <- Resource.eval(Slf4jLogger.create[IO])
        httpClient <- httpClientWith()
        tripSearchWorkflow <-
          val config = OllamaConfig(
            host = host"localhost",
            insecure = true,
            model = OllamaModel.PHI3,
            port = port"11434",
          )
          val chatModelProvider = ChatModelProvider(
            ollamaApiClient = OllamaApiClient.impl(
              config = config,
              httpClient = httpClient,
            ),
            config = config,
          )
          Resource.eval:
            for
              chatModel <- chatModelProvider.chatModel(verbose = false)
              tripSearchWorkflow = TripSearchWorkflow(
                accommodationsSearchAgent = AccommodationsSearchAgent.impl(
                  accommodationService = AccommodationService.impl,
                  chatModel = chatModel,
                  dateExtractor = DateExtractor.impl(chatModel),
                ),
                flightsSearchAgent = FlightsSearchAgent.impl(
                  flightService = FlightService.impl,
                  chatModel = chatModel,
                  dateExtractor = DateExtractor.impl(chatModel),
                ),
                mailWriterAgent = MailWriterAgent.impl(
                  chatModel = chatModel,
                  emailExtractor = EmailExtractor.impl(chatModel),
                ),
                mailSender = MailSender.impl(
                  from = "trip.agency@gmail.com",
                ),
                bookingService = BookingService.impl,
              )
            yield tripSearchWorkflow
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
            TripSearchWorkflow.TripSearchSignal.FindTrip(
              RequestId(tsid),
              "jane@example.org",
            ),
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
        yield ExitCode.Success,
    )
