package es.eriktorr
package trip_agent.infrastructure

import trip_agent.infrastructure.FakeTSIDGen.TSIDGenState

import cats.effect.{IO, Ref}
import io.hypersistence.tsid.TSID

final class FakeTSIDGen(
    stateRef: Ref[IO, TSIDGenState],
) extends TSIDGen[IO]:
  override def randomTSID: IO[TSID] =
    stateRef.flatModify: currentState =>
      val (headIO, next) = currentState.ids match
        case ::(head, next) => (IO.pure(head), next)
        case Nil =>
          (
            IO.raiseError(IllegalStateException("TSIDs exhausted")),
            List.empty,
          )
      (currentState.copy(next), headIO)

object FakeTSIDGen:
  final case class TSIDGenState(ids: List[TSID]):
    def set(newIds: List[TSID]): TSIDGenState =
      copy(newIds)

  object TSIDGenState:
    val empty: TSIDGenState = TSIDGenState(List.empty)
