package es.eriktorr
package trip_agent.application

import org.camunda.bpm.model.bpmn.Bpmn
import workflows4s.wio.DraftWorkflowContext.*
import weaver.FunSuite
import workflows4s.bpmn.BpmnRenderer

import java.io.File

object TripSearchWorkflowDraft extends FunSuite:
  test("should produce a draft".ignore):
    val findTrip: WIO.Draft = WIO.draft.signal(error = "Email not found")

    val findFlights: WIO.Draft = WIO.draft.step(error = "No flights found")
    val findHotels: WIO.Draft = WIO.draft.step(error = "No hotels found")

    val findTripOptions: WIO.Draft =
      WIO.draft.parallel(
        findFlights,
        findHotels,
      )

    val sendEmail: WIO.Draft = WIO.draft.step()

    val bookTrip: WIO.Draft = WIO.draft.signal(error = "Rejected")
    val completeSearch: WIO.Draft = WIO.draft.step()

    val cancelSearch: WIO.Draft = WIO.draft.step()

    val workflow: WIO.Draft =
      (
        findTrip >>>
          findTripOptions >>>
          sendEmail >>>
          bookTrip >>>
          completeSearch
      ).handleErrorWith(cancelSearch)

    val bpmnModel = BpmnRenderer.renderWorkflow(workflow.toProgress.toModel, "process")
    Bpmn.writeModelToFile(File(s"trip-search-draft.bpmn").getAbsoluteFile, bpmnModel)

    success
