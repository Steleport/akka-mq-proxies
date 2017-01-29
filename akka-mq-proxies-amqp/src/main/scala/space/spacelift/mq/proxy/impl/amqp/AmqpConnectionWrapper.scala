package space.spacelift.mq.proxy.impl.amqp

import java.util.UUID
import javax.inject.Inject

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.util.Timeout
import space.spacelift.amqp.Amqp.{ChannelParameters, ExchangeParameters, QueueParameters}
import space.spacelift.amqp.{Amqp, ConnectionOwner}
import com.rabbitmq.client.ConnectionFactory
import com.typesafe.config.Config
import space.spacelift.mq.proxy.ConnectionWrapper

import scala.concurrent.ExecutionContext.Implicits.global

class AmqpConnectionWrapper @Inject() (config: Config) extends ConnectionWrapper {
  private val connFactory = new ConnectionFactory()

  connFactory.setAutomaticRecoveryEnabled(true)
  connFactory.setTopologyRecoveryEnabled(false)
  connFactory.setHost(config.getString("spacelift.amqp.host"))
  connFactory.setPort(config.getInt("spacelift.amqp.port"))
  connFactory.setUsername(config.getString("spacelift.amqp.username"))
  connFactory.setPassword(config.getString("spacelift.amqp.password"))
  connFactory.setVirtualHost(config.getString("spacelift.amqp.vhost"))

  private var connectionOwner: Option[ActorRef] = None

  def getConnectionOwner(system: ActorSystem) = {
    if (connectionOwner.isEmpty) {
      connectionOwner = Some(system.actorOf(Props(new ConnectionOwner(connFactory))))
    }

    connectionOwner.get
  }

  override def wrapSubscriberActorOf(system: ActorSystem, realActor: ActorRef, name: String, timeout: Timeout): ActorRef = ???

  override def wrapPublisherActorOf(system: ActorSystem, realActor: ActorRef, name: String, timeout: Timeout): ActorRef = ???

  override def wrapRpcClientActorOf(system: ActorSystem, realActor: ActorRef, name: String, timeout: Timeout): ActorRef = ???

  override def wrapRpcServerActorOf(system: ActorSystem, realActor: ActorRef, name: String, timeout: Timeout): ActorRef = {
    val randuuid = UUID.randomUUID().toString

    val conn = getConnectionOwner(system)

    var exchangeName = name
    var queueName = name
    var isExchangePassive = true
    var exchangeType = "fanout"
    var isExchangeDurable = true
    var randomizeQueueName = true
    var isQueuePassive = true
    var isQueueAutodelete = true
    var isQueueDurable = true
    var qos = 1
    var isQosGlobal = false

    if (config.hasPath(s"spacelift.proxies.${name}")) {
      if (config.hasPath(s"spacelift.proxies.${name}.exchange.name")) {
        exchangeName = config.getString(s"spacelift.proxies.${name}.exchange.name")
      }
      if (config.hasPath(s"spacelift.proxies.${name}.exchange.passive")) {
        isExchangePassive = config.getBoolean(s"spacelift.proxies.${name}.exchange.passive")
      }
      if (config.hasPath(s"spacelift.proxies.${name}.exchange.type")) {
        exchangeType = config.getString(s"spacelift.proxies.${name}.exchange.type")
      }
      if (config.hasPath(s"spacelift.proxies.${name}.exchange.durable")) {
        isExchangeDurable = config.getBoolean(s"spacelift.proxies.${name}.exchange.durable")
      }
      if (config.hasPath(s"spacelift.proxies.${name}.queue.name")) {
        queueName = config.getString(s"spacelift.proxies.${name}.queue.name")
      }
      if (config.hasPath(s"spacelift.proxies.${name}.queue.randomizeName")) {
        randomizeQueueName = config.getBoolean(s"spacelift.proxies.${name}.queue.randomizeName")
      }
      if (config.hasPath(s"spacelift.proxies.${name}.queue.passive")) {
        isQueuePassive = config.getBoolean(s"spacelift.proxies.${name}.queue.passive")
      }
      if (config.hasPath(s"spacelift.proxies.${name}.queue.autodelete")) {
        isQueueAutodelete = config.getBoolean(s"spacelift.proxies.${name}.queue.autodelete")
      }
      if (config.hasPath(s"spacelift.proxies.${name}.queue.durable")) {
        isQueueDurable = config.getBoolean(s"spacelift.proxies.${name}.queue.durable")
      }
      if (config.hasPath(s"spacelift.proxies.${name}.channel.qos")) {
        qos = config.getInt(s"spacelift.proxies.${name}.channel.qos")
      }
      if (config.hasPath(s"spacelift.proxies.${name}.channel.global")) {
        isQosGlobal = config.getBoolean(s"spacelift.proxies.${name}.channel.global")
      }
    }

    // Create a wrapped connection Actor
    val exchange = ExchangeParameters(name = exchangeName, passive = isExchangePassive, exchangeType = exchangeType, durable = isExchangeDurable)
    val queue = QueueParameters(name = queueName + (if (randomizeQueueName) "-" + randuuid else ""), passive = isQueuePassive, autodelete = isQueueAutodelete, durable = isQueueDurable)
    val channelParams = ChannelParameters(qos = qos, global = isQosGlobal)

    println("Creating proxy actor for actor " + realActor)

    // Create our wrapped Actor of the original Actor
    val server = ConnectionOwner.createChildActor(conn,
      AmqpRpcServer.props(queue = queue, exchange = exchange, routingKey = name, proc = new AmqpProxy.ProxyServer(realActor), channelParams = channelParams),
      name = Some("proxy" + name + "-" + randuuid)
    )

    Amqp.waitForConnection(system, server).await()

    server
  }
}
