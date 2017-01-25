package space.spacelift.amqp

import Amqp._
import com.rabbitmq.client.AMQP.BasicProperties
import com.rabbitmq.client.{Envelope, Channel}
import util.{Failure, Success}
import akka.actor.{ActorRef, Props}
import akka.event.LoggingReceive
import scala.concurrent.ExecutionContext

object RpcServer {

  def props(processor: IProcessor, init: Seq[Request] = Seq.empty[Request], channelParams: Option[ChannelParameters] = None)(implicit ctx: ExecutionContext): Props =
    Props(new RpcServer(processor, init, channelParams))

  def props(queue: QueueParameters, exchange: ExchangeParameters, routingKey: String, proc: IProcessor, channelParams: ChannelParameters)(implicit ctx: ExecutionContext): Props =
    props(processor = proc, init = List(AddBinding(Binding(exchange, queue, routingKey))), channelParams = Some(channelParams))

  def props(queue: QueueParameters, exchange: ExchangeParameters, routingKey: String, proc: IProcessor)(implicit ctx: ExecutionContext): Props =
    props(processor = proc, init = List(AddBinding(Binding(exchange, queue, routingKey))))

}

/**
 * RPC Server, which
 * <ul>
 * <Li>consume messages from a set of queues</li>
 * <li>passes the message bodies to a "processor"</li>
 * <li>sends back the result queue specified in the "replyTo" property</li>
 * </ul>
 * @param processor [[space.spacelift.amqp.IProcessor]] implementation
 * @param channelParams optional channel parameters
 */
class RpcServer(processor: IProcessor, init: Seq[Request] = Seq.empty[Request], channelParams: Option[ChannelParameters] = None)(implicit ctx: ExecutionContext = ExecutionContext.Implicits.global) extends Consumer(listener = None, autoack = false, init = init, channelParams = channelParams) {

  private def sendResponse(result: ProcessResult, properties: BasicProperties, channel: Channel) {
    result match {
      // send a reply only if processor return something *and* replyTo is set
      case ProcessResult(Some(data), customProperties) if (properties.getReplyTo != null) => {
        // publish the response with the same correlation id as the request
        val props = customProperties.getOrElse(new BasicProperties()).builder().correlationId(properties.getCorrelationId).build()
        channel.basicPublish("", properties.getReplyTo, true, false, props, data)
      }
      case _ => {}
    }
  }

  override def connected(channel: Channel, forwarder: ActorRef) : Receive = LoggingReceive({
    case delivery@Delivery(consumerTag: String, envelope: Envelope, properties: BasicProperties, body: Array[Byte]) => {
      log.debug("processing delivery")
      processor.process(delivery).onComplete {
        case Success(result) => {
          sendResponse(result, properties, channel)
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
              val result = processor.onFailure(delivery, error)
              sendResponse(result, properties, channel)
              channel.basicReject(envelope.getDeliveryTag, false)
            }
          }
        }
      }
    }
  }: Receive) orElse super.connected(channel, forwarder)
}

