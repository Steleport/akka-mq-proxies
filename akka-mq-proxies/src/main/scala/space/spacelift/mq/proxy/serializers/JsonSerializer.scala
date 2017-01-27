package space.spacelift.mq.proxy.serializers

import akka.serialization.Serializer
import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.json4s.jackson.Serialization

object JsonSerializer extends Serializer {
  implicit val formats = org.json4s.DefaultFormats ++ org.json4s.ext.JodaTimeSerializers.all

  // scalastyle:off magic.number
  def identifier: Int = 123456789
  // scalastyle:on magic.number

  def includeManifest: Boolean = true

  def toBinary(o: AnyRef): Array[Byte] = Serialization.write(o).getBytes("UTF-8")

  def fromBinary(bytes: Array[Byte], manifest: Option[Class[_]]): AnyRef = {
    require(manifest.isDefined)
    val string = new String(bytes)
    implicit val mf = Manifest.classType(manifest.get)
    Serialization.read(string)
  }
}

