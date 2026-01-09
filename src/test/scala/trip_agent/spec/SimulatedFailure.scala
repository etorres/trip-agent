package es.eriktorr
package trip_agent.spec

import scala.util.control.NoStackTrace

object SimulatedFailure extends RuntimeException("Simulated failure") with NoStackTrace
