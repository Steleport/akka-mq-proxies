package space.spacelift.mq.proxy.patterns

import akka.actor.{Actor, ActorLogging}
import space.spacelift.mq.proxy.Delivery

object Publisher {
  case class Publish(publish: List[Delivery])

  object Publish {
    def apply(publish: Delivery): Publish = new Publish(List(publish))
  }
}

trait Publisher extends Actor with ActorLogging {

}
