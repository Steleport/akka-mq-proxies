package com.github.sstone.amqp.proxy.serializers

import org.xerial.snappy.Snappy
import akka.serialization.Serializer

/**
 * adds snappy compression/decompression to an existing serializer
 * @param serializer original serializer.
 */
abstract class SnappySerializer(serializer: Serializer) extends Serializer {

  // scalastyle:off magic.number
  def identifier: Int = 4
  // scalastyle:on magic.number

  def includeManifest: Boolean = true

  def toBinary(o: AnyRef): Array[Byte] = {
    val bytes = serializer.toBinary(o)
    val zipped = Snappy.compress(bytes)
    zipped
  }

  def fromBinary(bytes: Array[Byte], manifest: Option[Class[_]]): AnyRef = {
    val unzipped = Snappy.uncompress(bytes)
    serializer.fromBinary(unzipped, manifest)
  }

}

object SnappyJsonSerializer extends SnappySerializer(JsonSerializer)

object SnappyProtobufSerializer extends SnappySerializer(ProtobufSerializer)

object SnappyThriftSerializer extends SnappySerializer(ThriftSerializer)

