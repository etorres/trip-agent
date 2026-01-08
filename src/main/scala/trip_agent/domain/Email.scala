package es.eriktorr
package trip_agent.domain

import trip_agent.domain.TSIDCats.given

import cats.derived.*
import cats.implicits.{catsSyntaxEither, catsSyntaxEitherId}
import cats.{Eq, Show}
import io.circe.{Codec, Decoder, Encoder}
import io.hypersistence.tsid.TSID

final case class Email(
    messageId: Email.MessageId,
    recipient: Email.Address,
    subject: Email.Subject,
    body: Email.Body,
) derives Codec,
      Eq,
      Show

object Email:
  final case class MessageId(
      value: TSID,
  ) derives Codec,
        Eq,
        Show

  opaque type Address <: String = String

  object Address:
    def fromString(value: String): Either[Throwable, Address] =
      nonBlankString(value, "Email address")

    def applyUnsafe(value: String): Address =
      unsafeFrom(value, Address.fromString)

    given Eq[Address] = Eq.fromUniversalEquals

    given Show[Address] = Show.fromToString

    given Codec[Address] = codecFrom(Address.fromString)
  end Address

  opaque type Subject <: String = String

  object Subject:
    def fromString(value: String): Either[Throwable, Subject] =
      nonBlankString(value, "Email subject")

    def applyUnsafe(value: String): Subject =
      unsafeFrom(value, Subject.fromString)

    given Eq[Subject] = Eq.fromUniversalEquals

    given Show[Subject] = Show.fromToString

    given Codec[Subject] = codecFrom(Address.fromString)
  end Subject

  opaque type Body <: String = String

  object Body:
    def fromString(value: String): Either[Throwable, Body] =
      nonBlankString(value, "Email body")

    def applyUnsafe(value: String): Body =
      unsafeFrom(value, Body.fromString)

    given Eq[Body] = Eq.fromUniversalEquals

    given Show[Body] = Show.fromToString

    given Codec[Body] = codecFrom(Address.fromString)
  end Body

  private def nonBlankString(
      value: String,
      fieldName: String,
  ): Either[Throwable, String] =
    val sanitizedValue = value.trim
    if sanitizedValue.nonEmpty then sanitizedValue.asRight
    else IllegalArgumentException(s"$fieldName cannot be empty or blank").asLeft

  @SuppressWarnings(Array("org.wartremover.warts.Throw"))
  private def unsafeFrom[A <: String](
      value: String,
      builder: String => Either[Throwable, A],
  ): A =
    builder(value) match
      case Left(error) => throw error
      case Right(valid) => valid

  private def codecFrom[A <: String](
      builder: String => Either[Throwable, A],
  ): Codec[A] =
    Codec.from(
      Decoder.decodeString.emap: value =>
        builder(value).leftMap(_.getMessage),
      Encoder.encodeString.contramap[A](identity),
    )

  private lazy val emailPattern =
    "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}".r

  def findEmail(request: String): Option[String] =
    emailPattern.findFirstIn(request)
