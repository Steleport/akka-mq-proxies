package space.spacelift.mq.proxy.impl.amqp

import akka.actor.ActorSystem
import akka.pattern.ask
import akka.util.Timeout
import com.rabbitmq.client.ConnectionFactory
import space.spacelift.amqp.Amqp.{ExchangeParameters, Publish}
import space.spacelift.amqp.ConnectionOwner
import space.spacelift.mq.proxy.{Delivery, MessageProperties}
import space.spacelift.mq.proxy.patterns.RpcClient
import space.spacelift.mq.proxy.patterns.RpcClient.Request

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

/**
 * Created by fabrice on 31/12/13.
 */
object Test2 extends App {
  import ExecutionContext.Implicits.global

  implicit val system = ActorSystem("mySystem")
  implicit val timeout: Timeout = 1 second
  // create an AMQP connection
  val conn = system.actorOf(ConnectionOwner.props(new ConnectionFactory(), reconnectionDelay = 5 seconds), "connection")

  val client = ConnectionOwner.createChildActor(conn, AmqpRpcClient.props(ExchangeParameters("amq.direct", true, "direct"), "my_key"), Some("RpcClient"))

  // send 1 request every second
  while(true) {
    println("sending request")
    (client ? Request(Delivery("test".getBytes("UTF-8"), MessageProperties("String", "text/plain")))).mapTo[RpcClient.Response].map(response => {
      // we expect 1 delivery
      val delivery = response.deliveries.head
      println("reponse : " + new String(delivery.body))
    })
    Thread.sleep(1000)
  }

}
