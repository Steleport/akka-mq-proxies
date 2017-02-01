package space.spacelift.mq.proxy.serializers

import akka.serialization.Serializer

object Serializers {

  private val map = Map(
    "text/plain" -> NoOpSerializer,
    "application/json" -> JsonSerializer,
    "application/x-protobuf" -> ProtobufSerializer,
    "application/x-thrift" -> ThriftSerializer,
    "application/x-snappy-json" -> SnappyJsonSerializer,
    "application/x-snappy-protobuf" -> SnappyProtobufSerializer,
    "application/x-snappy-thrift" -> SnappyThriftSerializer)

  def contentTypeToSerializer(name: String): Serializer = map.getOrElse(name, JsonSerializer)

  def serializerToContentType(serializer: Serializer): String = map.map(_.swap).get(serializer).get
}
