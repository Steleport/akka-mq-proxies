package space.spacelift.mq.proxy.impl.amqp

import java.util.UUID
import javax.inject.Inject

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.util.Timeout
import space.spacelift.amqp.Amqp.{ChannelParameters, ExchangeParameters, QueueParameters}
import space.spacelift.amqp.{Amqp, ConnectionOwner}
import com.rabbitmq.client.ConnectionFactory
import com.typesafe.config.Config
import space.spacelift.mq.proxy.Proxy
import space.spacelift.mq.proxy.ConnectionWrapper
import space.spacelift.mq.proxy.patterns.{Processor, RpcClient}
import space.spacelift.mq.proxy.serializers.JsonSerializer

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

  def extractExchangeParameters(config: Config, name: String): ExchangeParameters = {
    var exchangeName = name
    var isExchangePassive = false
    var exchangeType = "fanout"
    var isExchangeDurable = true
    var isExchangeAutodelete = false

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
      if (config.hasPath(s"spacelift.proxies.${name}.exchange.durable")) {
        isExchangeDurable = config.getBoolean(s"spacelift.proxies.${name}.exchange.durable")
      }
      if (config.hasPath(s"spacelift.proxies.${name}.exchange.durable")) {
        isExchangeDurable = config.getBoolean(s"spacelift.proxies.${name}.exchange.durable")
      }
      if (config.hasPath(s"spacelift.proxies.${name}.exchange.autodelete")) {
        isExchangeAutodelete = config.getBoolean(s"spacelift.proxies.${name}.exchange.autodelete")
      }
    }

    ExchangeParameters(name = name, passive = isExchangePassive, exchangeType = exchangeType, durable = isExchangeDurable, autodelete = isExchangeAutodelete)
  }

  def extractAllParameters(config: Config, name: String): (ExchangeParameters, QueueParameters, ChannelParameters) = {
    var queueName = name
    var randomizeQueueName = true
    var isQueuePassive = false
    var isQueueAutodelete = true
    var isQueueDurable = true
    var qos = 1
    var isQosGlobal = false

    if (config.hasPath(s"spacelift.proxies.${name}")) {
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
    val exchange = extractExchangeParameters(config, name)
    val queue = QueueParameters(name = queueName + (if (randomizeQueueName) "-" + UUID.randomUUID().toString else ""), passive = isQueuePassive, autodelete = isQueueAutodelete, durable = isQueueDurable)
    val channelParams = ChannelParameters(qos = qos, global = isQosGlobal)

    (exchange, queue, channelParams)
  }

  override def wrapSubscriberActorOf(system: ActorSystem, realActor: ActorRef, name: String, timeout: Timeout): ActorRef = ???

  override def wrapPublisherActorOf(system: ActorSystem, realActor: ActorRef, name: String, timeout: Timeout): ActorRef = ???

  override def wrapRpcClientActorOf(system: ActorSystem, realActor: ActorRef, name: String, timeout: Timeout, clientProxy: (ActorRef => Actor)): ActorRef = {
    val conn = getConnectionOwner(system)

    system.log.debug("Creating proxy actor for actor " + realActor)

    val (exchange, queue, channelParams) = extractAllParameters(config, name)

    // Create client
    val client = ConnectionOwner.createChildActor(conn, AmqpRpcClient.props(exchange, name, queue, channelParams))

    val proxy = system.actorOf(Props(clientProxy(client)), name = "proxyClient" + name)

    Amqp.waitForConnection(system, client).await()

    proxy
  }

  override def wrapRpcServerActorOf(system: ActorSystem, realActor: ActorRef, name: String, timeout: Timeout, serverProxy: (ActorRef => Processor)): ActorRef = {
    val conn = getConnectionOwner(system)

    println("Creating proxy actor for actor " + realActor)

    val (exchange, queue, channelParams) = extractAllParameters(config, name)

    // Create our wrapped Actor of the original Actor
    val server = ConnectionOwner.createChildActor(conn,
      AmqpRpcServer.props(queue = queue, exchange = exchange, routingKey = name, proc = serverProxy(realActor), channelParams = channelParams),
      name = Some("proxyServer" + queue.name)
    )

    Amqp.waitForConnection(system, server).await()

    server
  }
}
