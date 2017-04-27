package space.spacelift.mq.proxy.impl.amqp

import akka.actor.{ActorRef, Props}
import akka.event.LoggingReceive
import com.rabbitmq.client.AMQP.BasicProperties
import com.rabbitmq.client.{Channel, DefaultConsumer, Envelope}
import space.spacelift.amqp.Amqp._
import space.spacelift.amqp.ChannelOwner
import space.spacelift.mq.proxy.patterns.Publisher

import scala.concurrent.ExecutionContext

object AmqpPublisher {
  def props(
             exchange: ExchangeParameters,
             routingKey: String,
             channelParams: Option[ChannelParameters] = None
           ): Props =
    Props(new AmqpPublisher(exchange, routingKey, true, false, channelParams))

  def props(
             exchange: ExchangeParameters,
             routingKey: String,
             channelParams: ChannelParameters
           )(implicit ctx: ExecutionContext): Props =
    props(exchange, routingKey, channelParams = Some(channelParams))

  def props(exchange: ExchangeParameters, routingKey: String)(implicit ctx: ExecutionContext): Props =
    props(exchange, routingKey)
}

class AmqpPublisher(
                     exchange: ExchangeParameters,
                     routingKey: String,
                     mandatory: Boolean,
                     immediate: Boolean,
                     channelParams: Option[ChannelParameters] = None
                   ) extends ChannelOwner(channelParams = channelParams) with Publisher {
  import Publisher._
  override def disconnected: Receive = LoggingReceive ({
    case request@Publish(publish) => {
      log.warning(s"not connected, cannot publish message")
    }
  }: Receive) orElse super.disconnected

  override def connected(channel: Channel, forwarder: ActorRef): Receive = LoggingReceive({
    case Publish(publish) => {
      log.debug(s"sending message")
      publish.foreach(p => {
        val props = new BasicProperties.Builder()
          .contentType(p.properties.contentType)
          .contentEncoding(p.properties.clazz)
          .build()

        channel.basicPublish(exchange.name, routingKey, mandatory, immediate, props, p.body)
      })
    }
    case msg@ReturnedMessage(replyCode, replyText, exchange, routingKey, properties, body) => {
      log.warning(s"unexpected returned message (code: $replyCode, text: $replyText): ${new String(body)}")
    }
  }: Receive) orElse super.connected(channel, forwarder)
}
