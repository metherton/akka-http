package part4_client

import java.util.UUID

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpMethods, HttpRequest, HttpResponse, StatusCodes, Uri}
import akka.stream.{ActorMaterializer, scaladsl}
import akka.stream.scaladsl.{Sink, Source}
import part4_client.PaymentSystemDomain.PaymentRequest

import scala.util.{Failure, Success}

object HostLevel extends App with PaymentJsonProtocol {

  implicit val system = ActorSystem("HostLevel")
  implicit val materializer = ActorMaterializer()
  import system.dispatcher


  val poolFlow = Http().cachedHostConnectionPool[Int]("www.google.com")

  Source(1 to 10)
    .map(i => (HttpRequest(), i))
    .via(poolFlow)
    .map {
      case (Success(response), value) =>
        // very important
        response.discardEntityBytes()
        s"Request $value has received response: $response"
      case (Failure(ex), value) =>
        s"Request $value has failed: $ex"
    }
  //  .runWith(Sink.foreach[String](println))

  import PaymentSystemDomain._
  import spray.json._

  val creditCards = List(
    CreditCard("4242-4242-4242-4242", "424", "ts-test-account"),
    CreditCard("1234-1234-1234-1234", "123", "ts-daniels-account"),
    CreditCard("1234-1234-4321-4321", "423", "ts-awesome-account")
  )

  val paymentRequests = creditCards.map(creditCard => PaymentRequest(creditCard, "rcjvm-store-account", 99))

  val serverHttpRequests = paymentRequests.map(paymentRequest =>
    (
      HttpRequest(
        HttpMethods.POST,
        uri = Uri("/api/payments"),
        entity = HttpEntity(
          ContentTypes.`application/json`,
          paymentRequest.toJson.prettyPrint
        )
      ),
      UUID.randomUUID().toString
    )
  )

  Source(serverHttpRequests)
    .via(Http().cachedHostConnectionPool[String]("localhost", 8080))
    .runForeach { // (Try[HttpResponse], String)
      case (Success(response@HttpResponse(StatusCodes.Forbidden,_,_,_)), orderId) =>
        println(s"The order id $orderId was not allowed to proceed: $response")
      case (Success(response), orderId) =>
        println(s"The order id $orderId was successful and returned response $response")
        // do something with the order ID: dispatch it, send notificaiton to customer etc
      case (Failure(ex), orderId) =>
        println(s"The order id $orderId could not be completed: $ex")
    }

  // high volume , low latency requests.


}
