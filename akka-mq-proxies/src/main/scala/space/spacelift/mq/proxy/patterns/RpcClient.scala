package space.spacelift.mq.proxy.patterns

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import space.spacelift.mq.proxy.Delivery

object RpcClient {
  case class Request(publish: List[Delivery], numberOfResponses: Int = 1)

  object Request {
    def apply(publish: Delivery) = new Request(List(publish), 1)
  }

  case class Response(deliveries: List[Delivery])

  case class Undelivered(msg: AnyRef)

  private[proxy] case class RpcResult(destination: ActorRef, expected: Int, deliveries: scala.collection.mutable.ListBuffer[Delivery])
}

trait RpcClient extends Actor with ActorLogging {
  import RpcClient._

  var counter: Int = 0
  var correlationMap = scala.collection.mutable.Map.empty[String, RpcResult]
}
