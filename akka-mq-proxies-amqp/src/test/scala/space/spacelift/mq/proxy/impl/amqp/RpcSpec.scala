package space.spacelift.mq.proxy.impl.amqp

import java.util.concurrent.TimeUnit

import akka.pattern.ask
import com.rabbitmq.client.AMQP.BasicProperties
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import space.spacelift.amqp.Amqp._
import space.spacelift.amqp.{Amqp, ConnectionOwner}
import space.spacelift.mq.proxy.patterns.RpcClient.{Request, Response, Undelivered}
import space.spacelift.mq.proxy.patterns.{ProcessResult, Processor}
import space.spacelift.mq.proxy.{MessageProperties, Delivery => ProxyDelivery}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

@RunWith(classOf[JUnitRunner])
class RpcSpec extends ChannelSpec {
  "RPC Servers" should {
    "reply to clients" in {
      val exchange = ExchangeParameters(name = "amq.direct", exchangeType = "", passive = true)
      val queue = randomQueue
      val routingKey = randomKey
      val proc = new Processor() {
        def process(delivery: ProxyDelivery) = Future {
          println("processing")
          val s = new String(delivery.body)
          if (s == "5") throw new Exception("I dont do 5s")
          ProcessResult(Some(delivery.body))
        }

        def onFailure(delivery: ProxyDelivery, e: Throwable) = ProcessResult(Some(e.toString.getBytes))
      }
      val server = ConnectionOwner.createChildActor(conn, AmqpRpcServer.props(queue, exchange, routingKey, proc))
      val client1 = ConnectionOwner.createChildActor(conn, AmqpRpcClient.props(exchange, routingKey))
      val client2 = ConnectionOwner.createChildActor(conn, AmqpRpcClient.props(exchange, routingKey))

      waitForConnection(system, conn, server, client1, client2).await()

      val f1 = Future {
        for (i <- 0 to 10) {
          try {
            val future = client1 ? Request(ProxyDelivery(i.toString.getBytes, MessageProperties("String", "text/plain")) :: Nil, 1)
            val result = Await.result(future, 1000.millis).asInstanceOf[Response]
            println("result1 " + new String(result.deliveries.head.body))
            Thread.sleep(300)
          }
          catch {
            case e: Exception => println(e.toString)
          }
        }
      }
      val f2 = Future {
        for (i <- 0 to 10) {
          try {
            val future = client2 ? Request(ProxyDelivery(i.toString.getBytes, MessageProperties("String", "text/plain")) :: Nil, 1)
            val result = Await.result(future, 1000.millis).asInstanceOf[Response]
            println("result2 " + new String(result.deliveries.head.body))
            Thread.sleep(300)
          }
          catch {
            case e: Exception => println(e.toString)
          }
        }
      }
      Await.result(f1, 1.minute)
      Await.result(f2, 1.minute)
    }

    "manage custom AMQP properties" in {
      val exchange = ExchangeParameters(name = "amq.direct", exchangeType = "", passive = true)
      val queue = randomQueue
      val routingKey = randomKey
      val proc = new Processor() {
        def process(delivery: ProxyDelivery) = Future {
          // return the same body with the same properties
          ProcessResult(Some(delivery.body), Some(delivery.properties))
        }

        def onFailure(delivery: ProxyDelivery, e: Throwable) = ProcessResult(Some(e.toString.getBytes), Some(delivery.properties))
      }
      val server = ConnectionOwner.createChildActor(conn, AmqpRpcServer.props(processor = proc), timeout = 2000.millis)
      val client = ConnectionOwner.createChildActor(conn, AmqpRpcClient.props(exchange, routingKey), timeout = 2000.millis)
      waitForConnection(system, conn, server, client).await(10, TimeUnit.SECONDS)
      server ! AddBinding(Binding(exchange, queue, routingKey))
      val Amqp.Ok(AddBinding(_), _) = receiveOne(1 second)

      val myprops = MessageProperties("my encoding", "my content")
      val future = client ? Request(ProxyDelivery("yo!!".getBytes, myprops) :: Nil, 1)
      val result = Await.result(future, 1000.millis).asInstanceOf[Response]
      val delivery = result.deliveries.head
      assert(delivery.properties.contentType === "my content")
      assert(delivery.properties.clazz === "my encoding")
    }
  }

  "RPC Clients" should {
    "correctly handle returned message" in {
      val client = ConnectionOwner.createChildActor(conn, AmqpRpcClient.props(ExchangeParameters("", true, "direct"), "mykey"), timeout = 2000.millis)
      waitForConnection(system, conn, client).await(2, TimeUnit.SECONDS)

      val future = client ? Request(ProxyDelivery("yo!".getBytes, MessageProperties("String", "text/plain")) :: Nil, 1)
      val result = Await.result(future, 1000.millis)
      assert(result.isInstanceOf[Undelivered])
    }
  }

  "RPC Clients and Servers" should {
    "implement 1 request/several responses patterns" in {
      val exchange = ExchangeParameters(name = "amq.direct", exchangeType = "", passive = true)
      // empty means that a random name will be generated when the queue is declared
      val queue = QueueParameters(name = "", passive = false, exclusive = true)
      // create 2 servers, each using a broker generated private queue and their own processor
      val proc1 = new Processor {
        def process(delivery: ProxyDelivery) = Future(ProcessResult(Some("proc1".getBytes)))

        def onFailure(delivery: ProxyDelivery, e: Throwable) = ProcessResult(None)
      }
      val routingKey = randomKey
      val server1 = ConnectionOwner.createChildActor(conn, AmqpRpcServer.props(proc1), timeout = 2000.millis)
      waitForConnection(system, conn, server1).await(5, TimeUnit.SECONDS)
      server1 ! AddBinding(Binding(exchange, queue, routingKey))
      val Amqp.Ok(AddBinding(_), _) = receiveOne(1 second)

      val proc2 = new Processor {
        def process(delivery: ProxyDelivery) = Future(ProcessResult(Some("proc2".getBytes)))

        def onFailure(delivery: ProxyDelivery, e: Throwable) = ProcessResult(None)
      }
      val server2 = ConnectionOwner.createChildActor(conn, AmqpRpcServer.props(proc2), timeout = 2000.millis)
      waitForConnection(system, conn, server2).await(5, TimeUnit.SECONDS)
      server2 ! AddBinding(Binding(exchange, queue, routingKey))
      val Amqp.Ok(AddBinding(_), _) = receiveOne(1 second)

      val client = ConnectionOwner.createChildActor(conn, AmqpRpcClient.props(exchange, routingKey), timeout = 2000.millis)
      waitForConnection(system, conn, client).await(5, TimeUnit.SECONDS)

      val future = client ? Request(ProxyDelivery("yo!".getBytes, MessageProperties("String", "text/plain")) :: Nil, 2)
      val result = Await.result(future, 2000.millis).asInstanceOf[Response]
      assert(result.deliveries.length === 2)
      // we're supposed to have received to answers, "proc1" and "proc2"
      val strings = result.deliveries.map(d => new String(d.body))
      assert(strings.sorted === List("proc1", "proc2"))
    }
  }
}
