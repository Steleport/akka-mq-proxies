package space.spacelift.mq.proxy

import akka.actor.ActorRef
import akka.serialization.Serializer
import akka.util.Timeout
import akka.pattern.ask
import org.slf4j.LoggerFactory
import space.spacelift.mq.proxy.patterns.{ProcessResult, Processor}
import space.spacelift.mq.proxy.serializers.{JsonSerializer, Serializers}

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

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

/**
  * Describes the properties of a message sent through the proxy
  *
  * @param clazz The fully qualified class name of the message
  * @param contentType The content type for serialization/deserialization purposes
  */
case class MessageProperties(clazz: String, contentType: String)

/**
  * Describes a delivered message
  *
  * @param body The message, serialized
  * @param properties The properties of the serialized message
  */
case class Delivery(body: Array[Byte], properties: MessageProperties)

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

  class ProxyServer(server: ActorRef, timeout: Timeout = 30 seconds) extends Processor {

    import ExecutionContext.Implicits.global

    lazy val logger = LoggerFactory.getLogger(classOf[ProxyServer])

    def process(delivery: Delivery): Future[ProcessResult] = {
      Try(deserialize(delivery.body, delivery.properties)) match {
        case Success((request, serializer)) => {
          logger.debug("handling delivery of type %s".format(request.getClass.getName))

          val future = for {
            response <- (server ? request)(timeout).mapTo[AnyRef]
            _ = logger.debug("sending response of type %s".format(response.getClass.getName))
            (body, props) = serialize(serializer, response)
          } yield ProcessResult(Some(body), Some(props))

          future.onFailure {
            case cause => logger.error(s"inner call to server actor $server failed", cause)
          }
          future
        }
        case Failure(cause) => {
          logger.error("deserialization failed", cause)
          Future.failed(cause)
        }
      }
    }

    def onFailure(delivery: Delivery, e: Throwable): ProcessResult = {
      val (body, props) = serialize(Serializers.contentTypeToSerializer(delivery.properties.contentType), ServerFailure(e.getMessage, e.toString))
      ProcessResult(Some(body), Some(props))
    }
  }
}
