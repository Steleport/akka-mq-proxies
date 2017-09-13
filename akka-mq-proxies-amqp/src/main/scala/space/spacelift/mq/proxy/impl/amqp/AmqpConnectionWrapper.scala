package space.spacelift.mq.proxy.impl.amqp

import java.io.FileInputStream
import java.security.KeyStore
import java.util.Map.Entry
import java.util.UUID
import javax.inject.Inject
import javax.net.ssl.{KeyManagerFactory, SSLContext, TrustManagerFactory}

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.util.Timeout
import space.spacelift.amqp.Amqp.{ChannelParameters, ExchangeParameters, QueueParameters}
import space.spacelift.amqp.{Amqp, ConnectionOwner, RabbitMQExtensions}
import com.rabbitmq.client.{ConnectionFactory, DefaultSaslConfig}
import com.typesafe.config.{Config, ConfigObject, ConfigValue}

import scala.collection.JavaConverters._
import space.spacelift.mq.proxy.ConnectionWrapper
import space.spacelift.mq.proxy.patterns.Processor

import scala.collection.concurrent.TrieMap
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Random

class AmqpConnectionWrapper @Inject() (config: Config) extends ConnectionWrapper {
  private val defaultConnFactory = new ConnectionFactory()

  // scalastyle:off magic.number
  private def randomChars: String = Random.alphanumeric.take(8).mkString
  // scalastyle:on magic.number

  if (config.hasPath("spacelift.amqp.useLegacySerializerEncodingSwap") && config.getBoolean("spacelift.amqp.useLegacySerializerEncodingSwap")) {
    space.spacelift.mq.proxy.Proxy.useLegacySerializerEncodingSwap = true
  }

  if (config.hasPath("spacelift.amqp.namespaceMappings")) {
    val list : Iterable[ConfigObject] = config.getObjectList("spacelift.amqp.namespaceMappings").asScala
    val map = (for {
      item : ConfigObject <- list
      entry : Entry[String, ConfigValue] <- item.entrySet().asScala
      key = entry.getKey
      value = entry.getValue.unwrapped().toString
    } yield (key, value)).toMap

    space.spacelift.mq.proxy.Proxy.namespaceMapping = map
  }

  defaultConnFactory.setAutomaticRecoveryEnabled(true)
  defaultConnFactory.setTopologyRecoveryEnabled(false)
  defaultConnFactory.setHost(config.getString("spacelift.amqp.host"))
  defaultConnFactory.setPort(config.getInt("spacelift.amqp.port"))
  defaultConnFactory.setUsername(config.getString("spacelift.amqp.username"))
  defaultConnFactory.setPassword(config.getString("spacelift.amqp.password"))
  defaultConnFactory.setVirtualHost(config.getString("spacelift.amqp.vhost"))

  if (config.hasPath("spacelift.amqp.ssl")) {
    defaultConnFactory.setSaslConfig(DefaultSaslConfig.EXTERNAL)

    val context = SSLContext.getInstance("TLSv1.1")

    val ks = KeyStore.getInstance("PKCS12")
    val kmf = KeyManagerFactory.getInstance("SunX509")
    val tks = KeyStore.getInstance("JKS")
    val tmf = TrustManagerFactory.getInstance("SunX509")
    ks.load(
      new FileInputStream(config.getString("spacelift.amqp.ssl.certificate.path")),
      config.getString("spacelift.amqp.ssl.certificate.password").toCharArray
    )
    kmf.init(ks, config.getString("spacelift.amqp.ssl.certificate.password").toCharArray)

    tks.load(new FileInputStream(config.getString("spacelift.amqp.ssl.cacerts.path")), config.getString("spacelift.amqp.ssl.cacerts.password").toCharArray())
    tmf.init(tks)

    // scalastyle:off null
    context.init(kmf.getKeyManagers, tmf.getTrustManagers, null)
    // scalastyle:on null

    defaultConnFactory.useSslProtocol(context)
  }

  private var connectionOwner: Option[ActorRef] = None
  private var federatedConnectionOwners = new TrieMap[String, ActorRef]()

  private def getConnectionOwner(system: ActorSystem, name: String) = {
    if (connectionOwner.isEmpty) {
      connectionOwner = Some(
        system.actorOf(
          Props(
            new ConnectionOwner(defaultConnFactory)
          ),
          s"connectionOwner-${defaultConnFactory.getVirtualHost.replace("/", "-")}-${randomChars}"
        )
      )
    }

    if (config.hasPath(s"spacelift.proxies.${name}.federation")) {
      var upstreamURI = ""
      var downstreamHost = ""
      var downstreamUser = ""
      var downstreamPass = ""
      var upstreamName = ""

      //upstreamURI: String, downstreamHost: String, downstreamUser: String, downstreamPass: String, upstreamName: String, vhost: String, pattern: String
      if (config.hasPath(s"spacelift.proxies.${name}.federation.upstream.uri")) {
        upstreamURI = config.getString(s"spacelift.proxies.${name}.federation.upstream.uri")
      }
      if (config.hasPath(s"spacelift.proxies.${name}.federation.upstream.name")) {
        upstreamName = config.getString(s"spacelift.proxies.${name}.federation.upstream.name")
      }
      if (config.hasPath(s"spacelift.proxies.${name}.federation.downstream.host")) {
        downstreamHost = config.getString(s"spacelift.proxies.${name}.federation.downstream.host")
      }
      if (config.hasPath(s"spacelift.proxies.${name}.federation.downstream.user")) {
        downstreamUser = config.getString(s"spacelift.proxies.${name}.federation.downstream.user")
      }
      if (config.hasPath(s"spacelift.proxies.${name}.federation.downstream.pass")) {
        downstreamPass = config.getString(s"spacelift.proxies.${name}.federation.downstream.pass")
      }

      RabbitMQExtensions.federateExchange(upstreamURI, downstreamHost, downstreamUser, downstreamPass, upstreamName, defaultConnFactory.getVirtualHost, s"^${name}$$")

      if (!federatedConnectionOwners.contains(upstreamURI)) {
        val federatedConnFactory = new ConnectionFactory()

        federatedConnFactory.setAutomaticRecoveryEnabled(true)
        federatedConnFactory.setTopologyRecoveryEnabled(false)
        federatedConnFactory.setHost(config.getString(s"spacelift.amqp.federation.${name}.host"))
        federatedConnFactory.setPort(config.getInt(s"spacelift.amqp.federation.${name}.port"))
        federatedConnFactory.setUsername(config.getString(s"spacelift.amqp.federation.${name}.username"))
        federatedConnFactory.setPassword(config.getString(s"spacelift.amqp.federation.${name}.password"))
        federatedConnFactory.setVirtualHost(config.getString(s"spacelift.amqp.federation.${name}.vhost"))

        if (config.hasPath(s"spacelift.amqp.federation.${name}.ssl")) {
          federatedConnFactory.setSaslConfig(DefaultSaslConfig.EXTERNAL)

          val context = SSLContext.getInstance("TLSv1.1")

          val ks = KeyStore.getInstance("PKCS12")
          val kmf = KeyManagerFactory.getInstance("SunX509")
          val tks = KeyStore.getInstance("JKS")
          val tmf = TrustManagerFactory.getInstance("SunX509")
          ks.load(
            new FileInputStream(config.getString(s"spacelift.amqp.federation.${name}.ssl.certificate.path")),
            config.getString(s"spacelift.amqp.federation.${name}.ssl.certificate.password").toCharArray
          )
          kmf.init(ks, config.getString(s"spacelift.amqp.federation.${name}.ssl.certificate.password").toCharArray)

          tks.load(new FileInputStream(config.getString(s"spacelift.amqp.federation.${name}.ssl.cacerts.path")), config.getString(s"spacelift.amqp.federation.${name}.ssl.cacerts.password").toCharArray())
          tmf.init(tks)

          // scalastyle:off null
          context.init(kmf.getKeyManagers, tmf.getTrustManagers, null)
          // scalastyle:on null

          federatedConnFactory.useSslProtocol(context)
        }

        federatedConnectionOwners.put(upstreamURI, system.actorOf(Props(new ConnectionOwner(federatedConnFactory)), s"connectionOwner-${name}-${randomChars}"))
      }

      federatedConnectionOwners(upstreamURI)
    } else {
      connectionOwner.get
    }
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

    ExchangeParameters(
      name = exchangeName,
      passive = isExchangePassive,
      exchangeType = exchangeType,
      durable = isExchangeDurable,
      autodelete = isExchangeAutodelete
    )
  }

  def extractAllParameters(config: Config, name: String): (ExchangeParameters, QueueParameters, ChannelParameters, String) = {
    var queueName = name
    var randomizeQueueName = true
    var isQueuePassive = false
    var isQueueAutodelete = true
    var isQueueDurable = true
    var qos = 1
    var isQosGlobal = false
    var routingKey = name

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
      if (config.hasPath(s"spacelift.proxies.${name}.queue.routingKey")) {
        routingKey = config.getString(s"spacelift.proxies.${name}.queue.routingKey")
      }
    }

    // Create a wrapped connection Actor
    val exchange = extractExchangeParameters(config, name)
    val queue = QueueParameters(
      name = queueName + (if (randomizeQueueName) "-" + UUID.randomUUID().toString else ""),
      passive = isQueuePassive,
      autodelete = isQueueAutodelete,
      durable = isQueueDurable
    )
    val channelParams = ChannelParameters(qos = qos, global = isQosGlobal)

    (exchange, queue, channelParams, routingKey)
  }

  override def wrapSubscriberActorOf(
                                      system: ActorSystem,
                                      realActor: ActorRef,
                                      name: String,
                                      timeout: Timeout,
                                      subscriberProxy: (ActorRef => Processor)
                                    ): ActorRef = {
    val conn = getConnectionOwner(system, name)

    system.log.debug("Creating proxy actor for actor " + realActor)

    val (exchange, queue, channelParams, routingKey) = extractAllParameters(config, name)

    // Create our wrapped Actor of the original Actor
    val server = ConnectionOwner.createChildActor(conn,
      AmqpSubscriber.props(queue = queue, exchange = exchange, routingKey = routingKey, proc = subscriberProxy(realActor), channelParams = channelParams),
      name = Some(s"proxySubscriber${queue.name}-${randomChars}")
    )

    Amqp.waitForConnection(system, server).await()

    server
  }

  override def wrapPublisherActorOf(system: ActorSystem, realActor: ActorRef, name: String, timeout: Timeout, publisherProxy: (ActorRef => Actor)): ActorRef = {
    val conn = getConnectionOwner(system, name)

    system.log.debug("Creating proxy actor for actor " + realActor)

    val (exchange, queue, channelParams, routingKey) = extractAllParameters(config, name)

    // Create client
    val client = ConnectionOwner.createChildActor(conn, AmqpPublisher.props(exchange, routingKey, channelParams), name = Some(s"connectionPublisher${name}-${randomChars}"))

    val proxy = system.actorOf(Props(publisherProxy(client)), name = s"proxyPublisher${name}-${randomChars}")

    Amqp.waitForConnection(system, client).await()

    proxy
  }

  override def wrapRpcClientActorOf(system: ActorSystem, realActor: ActorRef, name: String, timeout: Timeout, clientProxy: (ActorRef => Actor)): ActorRef = {
    val conn = getConnectionOwner(system, name)

    system.log.debug("Creating proxy actor for actor " + realActor)

    val (exchange, queue, channelParams, routingKey) = extractAllParameters(config, name)

    // Create client
    val client = ConnectionOwner.createChildActor(conn, AmqpRpcClient.props(exchange, routingKey, queue, channelParams), name = Some(s"connectionClient${name}-${randomChars}"))

    val proxy = system.actorOf(Props(clientProxy(client)), name = s"proxyClient${name}-${randomChars}")

    Amqp.waitForConnection(system, client).await()

    proxy
  }

  override def wrapRpcServerActorOf(
                                     system: ActorSystem,
                                     realActor: ActorRef,
                                     name: String,
                                     timeout: Timeout,
                                     serverProxy: (ActorRef => Processor)
                                   ): ActorRef = {
    val conn = getConnectionOwner(system, name)

    system.log.debug("Creating proxy actor for actor " + realActor)

    val (exchange, queue, channelParams, routingKey) = extractAllParameters(config, name)

    // Create our wrapped Actor of the original Actor
    val server = ConnectionOwner.createChildActor(conn,
      AmqpRpcServer.props(queue = queue, exchange = exchange, routingKey = routingKey, proc = serverProxy(realActor), channelParams = channelParams),
      name = Some(s"proxyServer${queue.name}-${randomChars}")
    )

    Amqp.waitForConnection(system, server).await()

    server
  }
}
