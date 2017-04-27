package space.spacelift.mq.proxy

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.util.Timeout
import space.spacelift.mq.proxy.patterns._
import space.spacelift.mq.proxy.serializers.JsonSerializer

trait ConnectionWrapper {
  def wrapRpcServerActorOf(system: ActorSystem, realActor: ActorRef, name: String, timeout: Timeout, serverProxy: (ActorRef => Processor)): ActorRef
  def wrapRpcClientActorOf(system: ActorSystem, realActor: ActorRef, name: String, timeout: Timeout, clientProxy: (ActorRef => Actor)): ActorRef
  def wrapPublisherActorOf(system: ActorSystem, realActor: ActorRef, name: String, timeout: Timeout, publisherProxy: (ActorRef => Actor)): ActorRef
  def wrapSubscriberActorOf(system: ActorSystem, realActor: ActorRef, name: String, timeout: Timeout, subscriberProxy: (ActorRef => Processor)): ActorRef
}
