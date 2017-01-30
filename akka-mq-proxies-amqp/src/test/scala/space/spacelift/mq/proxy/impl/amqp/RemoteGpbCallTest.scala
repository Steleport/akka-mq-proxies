package space.spacelift.mq.proxy.impl.amqp

import java.util.concurrent.TimeUnit

import akka.actor.{Actor, ActorSystem, Props}
import akka.pattern.ask
import akka.testkit.{ImplicitSender, TestKit}
import com.rabbitmq.client.ConnectionFactory
import org.junit.runner.RunWith
import org.scalatest.{Matchers, WordSpecLike}
import org.scalatest.junit.JUnitRunner
import space.spacelift.amqp.Amqp.{Binding, ChannelParameters, ExchangeParameters, QueueParameters, _}
import space.spacelift.amqp.{Amqp, ConnectionOwner}
import space.spacelift.mq.proxy.calculator.Calculator
import space.spacelift.mq.proxy.calculator.Calculator.{AddRequest, AddResponse}
import space.spacelift.mq.proxy.serializers.ProtobufSerializer

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

@RunWith(classOf[JUnitRunner])
class RemoteGpbCallTest extends TestKit(ActorSystem("TestSystem")) with ImplicitSender with WordSpecLike with Matchers {
  "AMQP Proxy" should {
    "handle GPB calls" in {
      import ExecutionContext.Implicits.global
      val connFactory = new ConnectionFactory()
      val conn = system.actorOf(Props(new ConnectionOwner(connFactory)), name = "conn")
      val exchange = ExchangeParameters(name = "amq.direct", exchangeType = "", passive = true)
      val queue = QueueParameters(name = "calculator-gpb", passive = false, autodelete = true)

      // create a simple calculator actor
      val calc = system.actorOf(Props(new Actor() {
        def receive = {
          case request: AddRequest => sender ! AddResponse.newBuilder().setX(request.getX).setY(request.getY).setSum(request.getX + request.getY).build()
        }
      }))
      // create an AMQP proxy server which consumes messages from the "calculator" queue and passes
      // them to our Calculator actor
      val server = ConnectionOwner.createChildActor(conn, AmqpRpcServer.props(new AmqpProxy.ProxyServer(calc), channelParams = Some(ChannelParameters(qos = 1))))
      Amqp.waitForConnection(system, server).await(5, TimeUnit.SECONDS)

      server ! AddBinding(Binding(exchange, queue, "calculator-gpb"))
      expectMsgPF() {
        case Amqp.Ok(AddBinding(_), _) => true
      }
      // create an AMQP proxy client in front of the "calculator queue"
      val client = ConnectionOwner.createChildActor(conn, AmqpRpcClient.props(ExchangeParameters("amq.direct", true, "direct"), "calculator-gpb"))
      val proxy = system.actorOf(Props(new AmqpProxy.ProxyClient(client, ProtobufSerializer)), name = "proxy")

      Amqp.waitForConnection(system, client).await(5, TimeUnit.SECONDS)
      implicit val timeout: akka.util.Timeout = 5 seconds

      val futures = for (x <- 0 until 5; y <- 0 until 5) yield (proxy ? AddRequest.newBuilder.setX(x).setY(y).build()).mapTo[AddResponse]
      val result = Await.result(Future.sequence(futures), 5 seconds)
      assert(result.length === 25)
      assert(result.filter(r => r.getSum != r.getX + r.getY).isEmpty)
    }
  }
}
