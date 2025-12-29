package es.eriktorr
package trip_agent.domain

object TripSearch:
  private lazy val emailPattern =
    "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}".r

  def findEmail(request: String): Option[String] =
    emailPattern.findFirstIn(request)
