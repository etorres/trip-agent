package es.eriktorr
package trip_agent.infrastructure.data.error

object ThrowableUtils:

  @SuppressWarnings(Array("org.wartremover.warts.Null"))
  /** Returns the deepest cause of a Throwable (or the Throwable itself if no cause).
    * @param throwable
    *   The error.
    * @return
    *   The deepest cause (or the original if there is no cause).
    */
  def rootCause(throwable: Throwable): Throwable =
    import scala.language.unsafeNulls
    Iterator
      .iterate(throwable)(_.getCause)
      .takeWhile(_ != null)
      .toList
      .lastOption
      .getOrElse(throwable)

  @SuppressWarnings(Array("org.wartremover.warts.Null"))
  /** Returns the full chain from the top-level Throwable to the root cause.
    * @param throwable
    *   The error.
    * @return
    *   The full chain of causes.
    */
  def allCauses(throwable: Throwable): List[Throwable] =
    import scala.language.unsafeNulls
    Iterator
      .iterate(throwable)(_.getCause)
      .takeWhile(_ != null)
      .toList
