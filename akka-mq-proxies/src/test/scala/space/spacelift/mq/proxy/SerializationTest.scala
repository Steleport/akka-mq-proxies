package space.spacelift.mq.proxy

import org.junit.Test
import org.scalatest.junit.AssertionsForJUnit
import space.spacelift.mq.proxy.gpbtest.Gpbtest
import space.spacelift.mq.proxy.serializers._
import space.spacelift.mq.proxy.thrifttest.Person

case class Message(a: String, b: Int)

class SerializationTest extends AssertionsForJUnit {

  @Test def verifyNoOpSerialization(): Unit = {
    val serializer = NoOpSerializer
    val msg = "Look at me, I'm a string!"

    val (body, props) = Proxy.serialize(serializer, msg)

    assert(new String(body) === msg)
    assert(props.contentType === "text/plain")
    assert(props.clazz === "java.lang.String")

    val (deserialized, _) = Proxy.deserialize(body, props)

    assert(deserialized === msg)
  }

  @Test def verifyJsonSerialization() {

    val serializer = JsonSerializer
    val msg = Message("toto", 123)

    val (body, props) = Proxy.serialize(serializer, msg)

    assert(new String(body) === """{"a":"toto","b":123}""")
    assert(props.contentType === "application/json")
    assert(props.clazz === "space.spacelift.mq.proxy.Message")

    val (deserialized, _) = Proxy.deserialize(body, props)

    assert(deserialized === msg)
  }

  @Test def verifySnappyJsonSerialization() {

    val serializer = SnappyJsonSerializer
    val msg = Message("toto", 123)

    val (body, props) = Proxy.serialize(serializer, msg)

    assert(props.contentType === "application/x-snappy-json")
    assert(props.clazz === "space.spacelift.mq.proxy.Message")

    val (deserialized, _) = Proxy.deserialize(body, props)

    assert(deserialized === msg)
  }

  @Test def verifyProtobufSerialization() {

    val serializer = ProtobufSerializer
    val msg = Gpbtest.Person.newBuilder().setId(123).setName("toto").setEmail("a@b.com").build

    val (body, props) = Proxy.serialize(serializer, msg)

    assert(props.contentType === "application/x-protobuf")
    assert(props.clazz === """space.spacelift.mq.proxy.gpbtest.Gpbtest$Person""")

    val (deserialized, _) = Proxy.deserialize(body, props)

    assert(deserialized === msg)
  }

  @Test def verifySnappyProtobufSerialization() {

    val serializer = SnappyProtobufSerializer
    val msg = Gpbtest.Person.newBuilder().setId(123).setName("toto").setEmail("a@b.com").build

    val (body, props) = Proxy.serialize(serializer, msg)

    assert(props.contentType === "application/x-snappy-protobuf")
    assert(props.clazz === """space.spacelift.mq.proxy.gpbtest.Gpbtest$Person""")

    val (deserialized, _) = Proxy.deserialize(body, props)

    assert(deserialized === msg)
  }

  @Test def verifyThriftSerialization() {

    val serializer = ThriftSerializer
    val msg = new Person().setId(123).setName("toto").setEmail("a@b.com")

    val (body, props) = Proxy.serialize(serializer, msg)

    assert(props.contentType === "application/x-thrift")
    assert(props.clazz === """space.spacelift.mq.proxy.thrifttest.Person""")

    val (deserialized, _) = Proxy.deserialize(body, props)

    assert(deserialized === msg)
  }

  @Test def verifySnappyThriftSerialization() {

    val serializer = SnappyThriftSerializer
    val msg = new Person().setId(123).setName("toto").setEmail("a@b.com")

    val (body, props) = Proxy.serialize(serializer, msg)

    assert(props.contentType === "application/x-snappy-thrift")
    assert(props.clazz === """space.spacelift.mq.proxy.thrifttest.Person""")

    val (deserialized, _) = Proxy.deserialize(body, props)

    assert(deserialized === msg)
  }

  @Test def verifDefaultSerialization() {
    val json = """{"a":"toto","b":123}"""
    val msg = Message("toto", 123)
    val props = MessageProperties("space.spacelift.mq.proxy.Message", null)
    val (deserialized, serializer) = Proxy.deserialize(json.getBytes("UTF-8"), props)
    assert(deserialized === msg)
    assert(serializer === JsonSerializer)
  }
}
