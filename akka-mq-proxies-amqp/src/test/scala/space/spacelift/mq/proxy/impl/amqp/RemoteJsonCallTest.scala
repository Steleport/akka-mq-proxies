package space.spacelift.mq.proxy.impl.amqp

import java.util.concurrent.TimeUnit

import akka.actor.{Actor, ActorSystem, Props}
import akka.pattern.ask
import akka.testkit.{ImplicitSender, TestKit}
import com.rabbitmq.client.ConnectionFactory
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.{Matchers, WordSpecLike}
import space.spacelift.amqp.Amqp.{AddBinding, ChannelParameters, ExchangeParameters, QueueParameters, _}
import space.spacelift.amqp.{Amqp, ConnectionOwner}
import space.spacelift.mq.proxy.Proxy
import space.spacelift.mq.proxy.serializers.JsonSerializer

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

object RemoteJsonCallTest {
  case class AddRequest(x: Int, y: Int)
  case class AddResponse(x: Int, y: Int, sum: Int)
}

@RunWith(classOf[JUnitRunner])
class RemoteJsonCallTest extends TestKit(ActorSystem("TestSystem")) with ImplicitSender with WordSpecLike with Matchers {

  import RemoteJsonCallTest._

  "AMQP Proxy" should {
    "handle JSON calls" in {
      import ExecutionContext.Implicits.global
      val connFactory = new ConnectionFactory()
      val conn = system.actorOf(Props(new ConnectionOwner(connFactory)), name = "conn")
      val exchange = ExchangeParameters(name = "amq.direct", exchangeType = "", passive = true)
      val queue = QueueParameters(name = "calculator-json", passive = false, autodelete = true)

      // create a simple calculator actor
      val calc = system.actorOf(Props(new Actor() {
        def receive = {
          case AddRequest(x, y) => sender ! AddResponse(x, y, x + y)
        }
      }))
      // create an AMQP proxy server which consumes messages from the "calculator" queue and passes
      // them to our Calculator actor
      val server = ConnectionOwner.createChildActor(conn, AmqpRpcServer.props(new Proxy.ProxyServer(calc), channelParams = Some(ChannelParameters(qos = 1))))
      Amqp.waitForConnection(system, server).await(5, TimeUnit.SECONDS)

      server ! AddBinding(Binding(exchange, queue, "calculator-json"))
      expectMsgPF() {
        case Amqp.Ok(AddBinding(_), _) => true
      }

      // create an AMQP proxy client in front of the "calculator queue"
      val client = ConnectionOwner.createChildActor(conn, AmqpRpcClient.props(ExchangeParameters("amq.direct", true, "direct"), "calculator-json"))
      val proxy = system.actorOf(Props(new Proxy.ProxyClient(client, JsonSerializer)),
        name = "proxy")

      Amqp.waitForConnection(system, client).await(5, TimeUnit.SECONDS)
      implicit val timeout: akka.util.Timeout = 5 seconds

      val futures = for (x <- 0 until 5; y <- 0 until 5) yield (proxy ? AddRequest(x, y)).mapTo[AddResponse]
      val result = Await.result(Future.sequence(futures), 5 seconds)
      assert(result.length === 25)
      assert(result.filter(r => r.sum != r.x + r.y).isEmpty)
    }
  }
}
