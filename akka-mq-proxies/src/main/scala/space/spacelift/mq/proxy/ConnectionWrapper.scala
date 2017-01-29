package space.spacelift.mq.proxy

import akka.actor.{ActorRef, Props}
import akka.util.Timeout

trait ConnectionWrapper {
  def wrapActorOf(props: Props, name: Option[String], timeout: Timeout): ActorRef
}
