package space.spacelift.mq.proxy.impl.amqp

import space.spacelift.amqp.Amqp.Delivery
import space.spacelift.mq.proxy._

import space.spacelift.mq.proxy.{Delivery => ProxyDelivery}

object AmqpProxy extends Proxy {
  def deliveryToProxyDelivery(delivery: Delivery) = {
    ProxyDelivery(delivery.body, MessageProperties(delivery.properties.getContentEncoding, delivery.properties.getContentType))
  }
}
