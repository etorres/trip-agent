package es.eriktorr
package trip_agent.infrastructure.security

import cats.implicits.toShow
import weaver.FunSuite

object SecretSuite extends FunSuite:
  test("should protect a secret from disclosure"):
    val secret = Secret("s3c4Et")
    expect(secret.value == "s3c4Et", "secret value") &&
    expect(secret.valueHash == "99559ca663009414306a65eb5a3dabb502d5129b", "secret hash") &&
    expect(secret.valueShortHash == "99559ca", "secret short hash") &&
    expect(secret.toString == s"Secret(99559ca)", "secret to string") &&
    expect(secret.show == s"Secret(99559ca)", "secret to string") &&
    matches(secret):
      case Secret(value) => expect(value == "s3c4Et", "secret unapply")
