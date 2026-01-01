package es.eriktorr
package trip_agent

import com.monovore.decline.Opts

final case class TripSearchParams(
    verbose: Boolean,
)

object TripSearchParams:
  def opts: Opts[TripSearchParams] =
    Opts
      .flag("verbose", short = "v", help = "Print extra information to the logs.")
      .orFalse
      .map(TripSearchParams.apply)
