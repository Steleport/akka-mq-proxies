object BasicRpcClient extends App {
  import ExecutionContext.Implicits.global

  implicit val system = ActorSystem("mySystem")
  implicit val timeout: Timeout = 5 seconds

  // create an AMQP connection
  val connFactory = new ConnectionFactory()
  connFactory.setUri("amqp://guest:guest@localhost/%2F")
  val conn = system.actorOf(ConnectionOwner.props(connFactory, 1 second))
  val client = ConnectionOwner.createChildActor(conn, RpcClient.props())

  // send 1 request every second
  while(true) {
    println("sending request")
    (client ? Request(Publish("amq.direct", "my_key", "test".getBytes("UTF-8")))).mapTo[RpcClient.Response].map(response => {
      // we expect 1 delivery
      val delivery = response.deliveries.head
      println("response : " + new String(delivery.body))
    })
    Thread.sleep(1000)
  }
}
