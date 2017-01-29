package space.spacelift.mq.proxy

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.util.Timeout
import space.spacelift.mq.proxy.patterns.{Publisher, RpcClient, RpcServer, Subscriber}

trait ConnectionWrapper {
  def wrapRpcServerActorOf(system: ActorSystem, realActor: ActorRef, name: String, timeout: Timeout): ActorRef
  def wrapRpcClientActorOf(system: ActorSystem, realActor: ActorRef, name: String, timeout: Timeout): ActorRef
  def wrapPublisherActorOf(system: ActorSystem, realActor: ActorRef, name: String, timeout: Timeout): ActorRef
  def wrapSubscriberActorOf(system: ActorSystem, realActor: ActorRef, name: String, timeout: Timeout): ActorRef
}
