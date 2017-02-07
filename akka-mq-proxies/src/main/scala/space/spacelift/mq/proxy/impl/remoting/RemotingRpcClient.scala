package space.spacelift.mq.proxy.impl.remoting

import akka.actor.Actor
import space.spacelift.mq.proxy.patterns.RpcClient

object RemotingRpcClient {
  // TODO: Add convenience props methods here
}

class RemotingRpcClient extends RpcClient {
  import RpcClient._

  // TODO: Retrieve remoting ActorRef and provide proxying logic
  override def receive: Actor.Receive = ???
}
