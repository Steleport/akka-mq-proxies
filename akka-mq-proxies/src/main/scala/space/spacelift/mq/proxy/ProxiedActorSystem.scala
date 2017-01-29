package space.spacelift.mq.proxy

import akka.actor.{ActorRef, ActorSystem, Props}

import scala.concurrent.duration._

trait ProxiedActorSystem {
  def proxyConnectionWrapper: ConnectionWrapper

  implicit class ProxiedActorOf(system: ActorSystem) {
    def rpcServerActorOf(props: Props, name: String): ActorRef = {
      val realActor = system.actorOf(props, name)

      proxyConnectionWrapper.wrapRpcServerActorOf(system, realActor, name, 5 seconds)
    }

    def rpcClientActorOf(props: Props, name: String): ActorRef = {
      val realActor = system.actorOf(props, name)

      proxyConnectionWrapper.wrapRpcServerActorOf(system, realActor, name, 5 seconds)
    }

    def publisherActorOf(props: Props, name: String): ActorRef = {
      val realActor = system.actorOf(props, name)

      proxyConnectionWrapper.wrapPublisherActorOf(system, realActor, name, 5 seconds)
    }

    def subscriberActorOf(props: Props, name: String): ActorRef = {
      val realActor = system.actorOf(props, name)

      proxyConnectionWrapper.wrapSubscriberActorOf(system, realActor, name, 5 seconds)
    }
  }
}

