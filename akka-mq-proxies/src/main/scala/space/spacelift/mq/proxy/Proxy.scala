package space.spacelift.mq.proxy

import akka.serialization.Serializer
import space.spacelift.mq.proxy.serializers.JsonSerializer

/**
  * Thrown when an error occurred on the "server" side and was sent back to the client
  * If you have a server Actor and create a proxy for it, then:
  * {{{
  *    proxy ? message
  * }}}
  * will behave as if you had written;
  * {{{
  *    server ? message
  * }}}
  * and server had sent back an `akka.actor.Status.ServerFailure(new ProxyException(message)))`
  *
  * @param message error message
  */
class ProxyException(message: String, throwableAsString: String) extends RuntimeException(message)
case class MessageProperties(clazz: String, contentType: String)

trait Proxy {
  /**
    * "server" side failure, that will be serialized and sent back to the client proxy
    *
    * @param message error message
    */
  protected case class ServerFailure(message: String, throwableAsString: String)

  def serialize(serializer: Serializer, msg: AnyRef): (Array[Byte], MessageProperties) = {
    (serializer.toBinary(msg), MessageProperties(msg.getClass.getName, Serializers.serializerToContentType(serializer)))
  }

  def deserialize(body: Array[Byte], props: MessageProperties): (AnyRef, Serializer) = {
    // scalastyle:off null
    require(props.clazz != null && props.clazz != "", "Class is not specified")
    val serializer = props.contentType match {
      case "" | null => JsonSerializer // use JSON if not serialization format was specified
      case contentType => Serializers.contentTypeToSerializer(contentType)
    }
    // scalastyle:on null

    (serializer.fromBinary(body, Some(Class.forName(props.clazz))), serializer)
  }
}
