package space.spacelift.mq.proxy.serializers

import akka.serialization.Serializer

object NoOpSerializer extends Serializer {
  // scalastyle:off magic.number
  def identifier: Int = 5
  // scalastyle:on magic.number

  def includeManifest: Boolean = true

  def toBinary(o: AnyRef): Array[Byte] = o.asInstanceOf[String].getBytes("UTF-8")

  def fromBinary(bytes: Array[Byte], manifest: Option[Class[_]]): AnyRef = new String(bytes)
}
