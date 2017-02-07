package space.spacelift.mq.proxy.impl.remoting

import akka.actor.Actor.Receive
import space.spacelift.mq.proxy.patterns.{Processor, RpcServer}

object RemotingRpcServer {
  // TODO: Add convenience props methods here
}

class RemotingRpcServer(val processor: Processor) extends RpcServer {
  // TODO: Add proxying logic
  override def receive: Receive = ???
}
