package es.eriktorr
package trip_agent.infrastructure.network

import io.circe.Codec
import io.circe.parser.decode
import io.circe.syntax.EncoderOps
import org.apache.pekko.serialization.Serializer

import scala.reflect.ClassTag
import scala.util.{Failure, Success}

@SuppressWarnings(Array("org.wartremover.warts.Throw"))
trait PekkoCirceSerializer[T <: AnyRef](using
    classTag: ClassTag[T],
    codec: Codec[T],
) extends Serializer:
  override def includeManifest: Boolean = true

  override def toBinary(obj: AnyRef): Array[Byte] =
    obj match
      case classTag(x) => x.asJson.noSpaces.getBytes
      case _ =>
        throw IllegalArgumentException(
          s"Unsupported type cannot be serialized: '${obj.getClass.getCanonicalName}'",
        )

  override def fromBinary(
      bytes: Array[Byte],
      manifest: Option[Class[?]],
  ): AnyRef =
    decode[T](String(bytes)).toTry match
      case Success(value) => value
      case Failure(exception) =>
        throw RuntimeException("Failed to deserialize object", exception)
