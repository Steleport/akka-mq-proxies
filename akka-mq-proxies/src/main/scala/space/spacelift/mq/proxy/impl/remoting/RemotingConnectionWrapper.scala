package space.spacelift.mq.proxy.impl.remoting

import javax.inject.Inject

import akka.actor.{Actor, ActorRef, ActorSystem}
import akka.util.Timeout
import com.typesafe.config.Config
import space.spacelift.mq.proxy.ConnectionWrapper
import space.spacelift.mq.proxy.patterns.Processor

class RemotingConnectionWrapper @Inject() (config: Config) extends ConnectionWrapper {
  override def wrapRpcServerActorOf(
                                     system: ActorSystem,
                                     realActor: ActorRef,
                                     name: String,
                                     timeout: Timeout,
                                     serverProxy: (ActorRef) => Processor
                                   ): ActorRef = ???

  override def wrapPublisherActorOf(system: ActorSystem, realActor: ActorRef, name: String, timeout: Timeout): ActorRef = ???

  override def wrapRpcClientActorOf(system: ActorSystem, realActor: ActorRef, name: String, timeout: Timeout, clientProxy: (ActorRef) => Actor): ActorRef = ???

  override def wrapSubscriberActorOf(system: ActorSystem, realActor: ActorRef, name: String, timeout: Timeout): ActorRef = ???
}
