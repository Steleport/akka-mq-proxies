package space.spacelift.mq.proxy

import javax.inject.{Inject, Singleton}

import akka.actor.{ActorRef, ActorSystem, Props}

import scala.concurrent.duration._
import scala.util.Random

@Singleton
class ProxiedActorSystem @Inject() (proxyConnectionWrapper: ConnectionWrapper) {
  def randomChars = Random.alphanumeric.take(8).mkString

  implicit class ProxiedActorOf(system: ActorSystem) {
    def rpcServerActorOf(props: Props, name: String): ActorRef = {
      val realActor = system.actorOf(props, s"rpcServer${name}-${randomChars}")

      proxyConnectionWrapper.wrapRpcServerActorOf(system, realActor, name, 5 seconds)
    }

    def rpcClientActorOf(props: Props, name: String): ActorRef = {
      val realActor = system.actorOf(props, s"rpcClient${name}-${randomChars}")

      proxyConnectionWrapper.wrapRpcClientActorOf(system, realActor, name, 5 seconds)
    }

    def publisherActorOf(props: Props, name: String): ActorRef = {
      val realActor = system.actorOf(props, s"publisher${name}-${randomChars}")

      proxyConnectionWrapper.wrapPublisherActorOf(system, realActor, name, 5 seconds)
    }

    def subscriberActorOf(props: Props, name: String): ActorRef = {
      val realActor = system.actorOf(props, s"subscriber${name}-${randomChars}")

      proxyConnectionWrapper.wrapSubscriberActorOf(system, realActor, name, 5 seconds)
    }
  }
}

