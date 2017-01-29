package space.spacelift.mq.proxy

import serializers._
import akka.serialization.Serializer

object Serializers {

  private val map = Map(
    "json" -> JsonSerializer,
    "protobuf" -> ProtobufSerializer,
    "thrift" -> ThriftSerializer,
    "snappy-json" -> SnappyJsonSerializer,
    "snappy-protobuf" -> SnappyProtobufSerializer,
    "snappy-thrift" -> SnappyThriftSerializer)

  def contentTypeToSerializer(name: String): Serializer = map.getOrElse(name, JsonSerializer)

  def serializerToContentType(serializer: Serializer): String = map.map(_.swap).get(serializer).get
}
