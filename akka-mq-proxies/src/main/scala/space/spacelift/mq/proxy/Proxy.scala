package space.spacelift.mq.proxy

/**
  * Thrown when an error occurred on the "server" side and was sent back to the client
  * If you have a server Actor and create a proxy for it, then:
  * {{{
  *    proxy ? message
  * }}}
  * will behave as if you had written;
  * {{{
  *    server ? message
  * }}}
  * and server had sent back an `akka.actor.Status.ServerFailure(new ProxyException(message)))`
  * @param message error message
  */
class ProxyException(message: String, throwableAsString: String) extends RuntimeException(message)

trait Proxy {
  /**
    * "server" side failure, that will be serialized and sent back to the client proxy
    * @param message error message
    */
  protected case class ServerFailure(message: String, throwableAsString: String)

}
