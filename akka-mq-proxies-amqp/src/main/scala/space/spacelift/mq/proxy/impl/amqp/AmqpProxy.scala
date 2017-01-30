package space.spacelift.mq.proxy.impl.amqp

import space.spacelift.amqp.Amqp.Delivery
import space.spacelift.mq.proxy.{MessageProperties, Delivery => ProxyDelivery}

object AmqpProxy {
  def deliveryToProxyDelivery(delivery: Delivery) = {
    ProxyDelivery(delivery.body, MessageProperties(delivery.properties.getContentEncoding, delivery.properties.getContentType))
  }
}
