package space.spacelift.mq.proxy

import org.junit.Test
import org.scalatest.junit.AssertionsForJUnit
import space.spacelift.mq.proxy.gpbtest.Gpbtest
import space.spacelift.mq.proxy.serializers._
import space.spacelift.mq.proxy.thrifttest.Person

case class Message(a: String, b: Int)

class SerializationTest extends AssertionsForJUnit with Proxy {

  @Test def verifyJsonSerialization() {

    val serializer = JsonSerializer
    val msg = Message("toto", 123)

    val (body, props) = super.serialize(serializer, msg)

    assert(new String(body) === """{"a":"toto","b":123}""")
    assert(props.contentType === "application/json")
    assert(props.clazz === "space.spacelift.amqp.proxy.Message")

    val (deserialized, _) = super.deserialize(body, props)

    assert(deserialized === msg)
  }

  @Test def verifySnappyJsonSerialization() {

    val serializer = SnappyJsonSerializer
    val msg = Message("toto", 123)

    val (body, props) = super.serialize(serializer, msg)

    assert(props.contentType === "application/x-snappy-json")
    assert(props.clazz === "space.spacelift.amqp.proxy.Message")

    val (deserialized, _) = super.deserialize(body, props)

    assert(deserialized === msg)
  }

  @Test def verifyProtobufSerialization() {

    val serializer = ProtobufSerializer
    val msg = Gpbtest.Person.newBuilder().setId(123).setName("toto").setEmail("a@b.com").build

    val (body, props) = super.serialize(serializer, msg)

    assert(props.contentType === "application/x-protobuf")
    assert(props.clazz === """space.spacelift.amqp.proxy.gpbtest.Gpbtest$Person""")

    val (deserialized, _) = super.deserialize(body, props)

    assert(deserialized === msg)
  }

  @Test def verifySnappyProtobufSerialization() {

    val serializer = SnappyProtobufSerializer
    val msg = Gpbtest.Person.newBuilder().setId(123).setName("toto").setEmail("a@b.com").build

    val (body, props) = super.serialize(serializer, msg)

    assert(props.contentType === "application/x-snappy-protobuf")
    assert(props.clazz === """space.spacelift.amqp.proxy.gpbtest.Gpbtest$Person""")

    val (deserialized, _) = super.deserialize(body, props)

    assert(deserialized === msg)
  }

  @Test def verifyThriftSerialization() {

    val serializer = ThriftSerializer
    val msg = new Person().setId(123).setName("toto").setEmail("a@b.com")

    val (body, props) = super.serialize(serializer, msg)

    assert(props.contentType === "application/x-thrift")
    assert(props.clazz === """space.spacelift.amqp.proxy.thrifttest.Person""")

    val (deserialized, _) = super.deserialize(body, props)

    assert(deserialized === msg)
  }

  @Test def verifySnappyThriftSerialization() {

    val serializer = SnappyThriftSerializer
    val msg = new Person().setId(123).setName("toto").setEmail("a@b.com")

    val (body, props) = super.serialize(serializer, msg)

    assert(props.contentType === "application/x-snappy-thrift")
    assert(props.clazz === """space.spacelift.amqp.proxy.thrifttest.Person""")

    val (deserialized, _) = super.deserialize(body, props)

    assert(deserialized === msg)
  }

  @Test def verifDefaultSerialization() {
    val json = """{"a":"toto","b":123}"""
    val msg = Message("toto", 123)
    val props = MessageProperties("space.spacelift.amqp.proxy.Message", null)
    val (deserialized, serializer) = super.deserialize(json.getBytes("UTF-8"), props)
    assert(deserialized === msg)
    assert(serializer === JsonSerializer)
  }
}
