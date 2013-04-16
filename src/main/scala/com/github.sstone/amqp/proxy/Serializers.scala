package com.github.sstone.amqp.proxy

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

  def nameToSerializer(name: String) = map.getOrElse(name, JsonSerializer)

  def serializerToName(serializer: Serializer) = map.map(_.swap).get(serializer).get
}
