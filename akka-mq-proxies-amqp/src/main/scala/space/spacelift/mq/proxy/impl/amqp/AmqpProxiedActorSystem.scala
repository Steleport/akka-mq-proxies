package space.spacelift.mq.proxy.impl.amqp

import com.typesafe.config.Config
import space.spacelift.mq.proxy.{ConnectionWrapper, ProxiedActorSystem}

import javax.inject.{Inject, Singleton}

@Singleton
class AmqpProxiedActorSystem @Inject() (proxyConnectionWrapper: AmqpConnectionWrapper) extends ProxiedActorSystem {
  override def proxyConnectionWrapper: ConnectionWrapper = ???
}
