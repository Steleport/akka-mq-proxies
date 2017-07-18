package space.spacelift.mq.proxy

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.serialization.Serializer
import akka.util.Timeout
import akka.pattern.{ask, pipe}
import org.slf4j.LoggerFactory
import space.spacelift.mq.proxy.patterns.{ProcessResult, Processor, Publisher, RpcClient}
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

object Proxy {
  /**
    * In pre-2.0, the contentType had the name of the class, and contentEncoding had the name of the content type. Don't
    * enable this unless you know you need it.
    */
  var useLegacySerializerEncodingSwap = false

  var namespaceMapping: Map[String, String] = Map()

  /**
    * "server" side failure, that will be serialized and sent back to the client proxy
    *
    * @param message error message
    */
  case class ServerFailure(message: String, throwableAsString: String)

  def serialize(serializer: Serializer, msg: AnyRef): (Array[Byte], MessageProperties) = {
    (serializer.toBinary(msg),
      if (useLegacySerializerEncodingSwap) {
        MessageProperties(
          Serializers.serializerToContentType(serializer),
          msg.getClass.getName.split('.').toList.reverse match {
            case x :: xs => namespaceMapping.getOrElse(xs.reverse.mkString("."), xs.reverse.mkString(".")) + s".${x}"
          }
        )
      } else {
        MessageProperties(
          msg.getClass.getName.split('.').toList.reverse match {
            case x :: xs => namespaceMapping.getOrElse(xs.reverse.mkString("."), xs.reverse.mkString(".")) + s".${x}"
          },
          Serializers.serializerToContentType(serializer)
        )
      })
  }

  def deserialize(body: Array[Byte], props: MessageProperties): (AnyRef, Serializer) = {
    // scalastyle:off null
    require(props.clazz != null && props.clazz != "", "Class is not specified")

    val serializer = (if (useLegacySerializerEncodingSwap) { props.clazz } else { props.contentType }) match {
      case "" | null => JsonSerializer // use JSON if not serialization format was specified
      case contentType => Serializers.contentTypeToSerializer(contentType)
    }
    // scalastyle:on null

    (serializer.fromBinary(body, Some(Class.forName(
      (if (useLegacySerializerEncodingSwap) { props.contentType } else { props.clazz }).split('.').toList.reverse match {
        case x :: xs => namespaceMapping.map(_.swap).getOrElse(xs.reverse.mkString("."), xs.reverse.mkString(".")) + s".${x}"
      }
    ))), serializer)
  }

  class ProxyServer(server: ActorRef, timeout: Timeout = 30 seconds) extends Processor {

    import ExecutionContext.Implicits.global

    lazy val logger = LoggerFactory.getLogger(classOf[ProxyServer])

    def process(delivery: Delivery): Future[ProcessResult] = {
      Try(deserialize(delivery.body, delivery.properties)) match {
        case Success((request, serializer)) => {
          logger.debug("handling delivery of type %s with serializer %s".format(request.getClass.getName, serializer.getClass.getName))

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
      val (body, props) = serialize(Serializers.contentTypeToSerializer(
        if (useLegacySerializerEncodingSwap) {
          delivery.properties.clazz
        } else {
          delivery.properties.contentType
        }
      ), ServerFailure(e.getMessage, e.toString))
      ProcessResult(Some(body), Some(props))
    }
  }

  object ProxyClient {
    /**
      * Defines a ProxyClient with a default serializer
      * @param client The RPC Client
      * @return Props containing the ProxyClient
      */
    def props(client: ActorRef): Props = Props(new ProxyClient(client, JsonSerializer))
  }

  /**
    * standard  one-request/one response proxy, which allows to write (myActor ? MyRequest).mapTo[MyResponse]
    * @param client RPC Client
    */
  class ProxyClient(client: ActorRef, serializer: Serializer, timeout: Timeout = 30 seconds) extends Actor {

    import ExecutionContext.Implicits.global

    def receive: Actor.Receive = {
      case msg: AnyRef => {
        Try(serialize(serializer, msg)) match {
          case Success((body, props)) => {
            // publish the serialized message (and tell the RPC client that we expect one response)
            val publish = Delivery(body, props)
            val future = (client ? RpcClient.Request(publish :: Nil, 1))(timeout).mapTo[AnyRef].map {
              case result : RpcClient.Response => {
                val delivery = result.deliveries(0)
                val (response, serializer) = deserialize(delivery.body, delivery.properties)
                response match {
                  case ServerFailure(message, throwableAsString) => akka.actor.Status.Failure(new ProxyException(message, throwableAsString))
                  case _ => response
                }
              }
              case undelivered : RpcClient.Undelivered => undelivered
            }

            future.pipeTo(sender)
          }
          case Failure(cause) => sender ! akka.actor.Status.Failure(new ProxyException("Serialization error", cause.getMessage))
        }
      }
    }
  }

  object ProxySender {
    /**
      * Defines a ProxySender with a default serializer
      * @param client RPC Client
      * @return Props containing the ProxySender
      */
    def props(client: ActorRef): Props = Props(new ProxySender(client, JsonSerializer))
  }

  /**
    * "fire-and-forget" proxy, which allows to write myActor ! MyRequest
    * TODO: Change this to use a Publisher rather than an RPC Client
    * @param client RPC Client
    */
  class ProxySender(client: ActorRef, serializer: Serializer) extends Actor with ActorLogging {

    def receive: Actor.Receive = {
      case msg: AnyRef => {
        val (body, props) = serialize(serializer, msg)
        val publish = Delivery(body, props)
        log.debug("sending %s to %s".format(publish, client))
        client ! Publisher.Publish(publish)
      }
    }
  }
}
