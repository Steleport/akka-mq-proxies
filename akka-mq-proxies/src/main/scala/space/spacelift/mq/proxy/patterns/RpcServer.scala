package space.spacelift.mq.proxy.patterns

import akka.actor.{Actor, ActorLogging}

trait RpcServer extends Actor with ActorLogging {
  def processor: Processor
}
