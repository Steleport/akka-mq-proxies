package space.spacelift.mq.proxy

import javax.inject.{Inject, Singleton}

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import space.spacelift.mq.proxy.patterns.Processor
import space.spacelift.mq.proxy.serializers.JsonSerializer

import scala.concurrent.duration._
import scala.util.Random

@Singleton
class ProxiedActorSystem @Inject() (proxyConnectionWrapper: ConnectionWrapper) {
  // scalastyle:off magic.number
  private def randomChars: String = Random.alphanumeric.take(8).mkString
  // scalastyle:on magic.number

  implicit class ProxiedActorOf(system: ActorSystem) {
    def rpcServerActorOf(props: Props, name: String, serverProxy: (ActorRef => Processor) = new Proxy.ProxyServer(_)): ActorRef = {
      val realActor = system.actorOf(props, s"rpcServer${name}-${randomChars}")

      proxyConnectionWrapper.wrapRpcServerActorOf(system, realActor, name, 5 seconds, serverProxy)
    }

    def rpcClientActorOf(props: Props, name: String, clientProxy: (ActorRef => Actor) = new Proxy.ProxyClient(_, JsonSerializer)): ActorRef = {
      val realActor = system.actorOf(props, s"rpcClient${name}-${randomChars}")

      proxyConnectionWrapper.wrapRpcClientActorOf(system, realActor, name, 5 seconds, clientProxy)
    }

    def publisherActorOf(props: Props, name: String, publisherProxy: (ActorRef => Actor) = new Proxy.ProxySender(_, JsonSerializer)): ActorRef = {
      val realActor = system.actorOf(props, s"publisher${name}-${randomChars}")

      proxyConnectionWrapper.wrapPublisherActorOf(system, realActor, name, 5 seconds, publisherProxy)
    }

    def subscriberActorOf(props: Props, name: String, subscriberProxy: (ActorRef => Processor)): ActorRef = {
      val realActor = system.actorOf(props, s"subscriber${name}-${randomChars}")

      proxyConnectionWrapper.wrapSubscriberActorOf(system, realActor, name, 5 seconds, subscriberProxy)
    }
  }
}

