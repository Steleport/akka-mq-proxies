package space.spacelift.mq.proxy.impl.amqp

import akka.actor.{ActorRef, Props}
import akka.event.LoggingReceive
import com.rabbitmq.client.AMQP.BasicProperties
import com.rabbitmq.client.{Channel, Envelope}
import space.spacelift.amqp.Amqp._
import space.spacelift.amqp.Consumer
import space.spacelift.mq.proxy.MessageProperties
import space.spacelift.mq.proxy.patterns.{ProcessResult, Processor, RpcServer, Subscriber}

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

object AmqpSubscriber {

  def props(
             processor: Processor,
             init: Seq[Request] = Seq.empty[Request],
             channelParams: Option[ChannelParameters] = None
           )(implicit ctx: ExecutionContext): Props =
    Props(new AmqpSubscriber(processor, init, channelParams))

  def props(
             queue: QueueParameters,
             exchange: ExchangeParameters,
             routingKey: String,
             proc: Processor,
             channelParams: ChannelParameters
           )(implicit ctx: ExecutionContext): Props =
    props(processor = proc, init = List(AddBinding(Binding(exchange, queue, routingKey))), channelParams = Some(channelParams))

  def props(queue: QueueParameters, exchange: ExchangeParameters, routingKey: String, proc: Processor)(implicit ctx: ExecutionContext): Props =
    props(processor = proc, init = List(AddBinding(Binding(exchange, queue, routingKey))))

}

/**
  * Subscriber, which
  * <ul>
  * <Li>consumes messages from a set of queues</li>
  * <li>passes the message bodies to a "processor"</li>
  * </ul>
  *
  * @param processor    [[Processor]] implementation
  * @param channelParams optional channel parameters
  */
class AmqpSubscriber(
                     val processor: Processor,
                     init: Seq[Request] = Seq.empty[Request],
                     channelParams: Option[ChannelParameters] = None
                   )(implicit ctx: ExecutionContext = ExecutionContext.Implicits.global)
  extends Consumer(
    listener = None,
    autoack = false,
    init = init,
    channelParams = channelParams
  ) with Subscriber {

  override def connected(channel: Channel, forwarder: ActorRef) : Receive = LoggingReceive({
    case delivery@Delivery(consumerTag: String, envelope: Envelope, properties: BasicProperties, body: Array[Byte]) => {
      log.debug("processing delivery")
      val proxyDelivery = AmqpProxy.deliveryToProxyDelivery(delivery)
      processor.process(proxyDelivery).onComplete {
        case Success(result) => {
          channel.basicAck(envelope.getDeliveryTag, false)
        }
        case Failure(error) => {
          envelope.isRedeliver match {
            // first failure: reject and requeue the message
            case false => {
              log.error(error, "processing {} failed, rejecting message", delivery)
              channel.basicReject(envelope.getDeliveryTag, true)
            }
            // second failure: reply with an error message, reject (but don't requeue) the message
            case true => {
              log.error(error, "processing {} failed for the second time, acking message", delivery)
              val result = processor.onFailure(proxyDelivery, error)
              channel.basicReject(envelope.getDeliveryTag, false)
            }
          }
        }
      }
    }
  }: Receive) orElse super.connected(channel, forwarder)
}

