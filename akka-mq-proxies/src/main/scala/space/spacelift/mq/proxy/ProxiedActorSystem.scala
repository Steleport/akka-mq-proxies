package space.spacelift.mq.proxy

import akka.actor.{ActorRef, ActorSystem, Props}

import scala.concurrent.duration._

trait ProxiedActorSystem {
  def proxyConnectionWrapper: ConnectionWrapper

  implicit class ProxiedActorOf(system: ActorSystem) {
    def rpcServerActorOf(props: Props, name: String): ActorRef = {
      val realActor = system.actorOf(props, name)

      proxyConnectionWrapper.wrapActorOf(props, Some(name), 5 seconds)
    }
  }
}

