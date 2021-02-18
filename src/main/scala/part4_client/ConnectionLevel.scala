package part4_client

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpMethods, HttpRequest, HttpResponse, Uri}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}

import scala.util.{Failure, Success}
import spray.json._

object ConnectionLevel extends App with PaymentJsonProtocol {


  implicit val system = ActorSystem("ConnectionLevel")
  implicit val materializer = ActorMaterializer()
  import system.dispatcher

//  val connectionFlow = Http().outgoingConnection("www.google.com")
//
//  def oneOffRequest(request: HttpRequest) =
//    Source.single(request).via(connectionFlow).runWith(Sink.head)
//
//  oneOffRequest(HttpRequest()).onComplete {
//    case Success(response) => println(s"Got successful response: $response")
//    case Failure(ex) => println(s"sending the request failed; $ex")
//  }

  /*
      A small payment system
   */

  import PaymentSystemDomain._

  val creditCards = List(
    CreditCard("4242-4242-4242-4242", "424", "ts-test-account"),
    CreditCard("1234-1234-1234-1234", "123", "ts-daniels-account"),
    CreditCard("1234-1234-4321-4321", "423", "ts-awesome-account")
  )

  val paymentRequests = creditCards.map(creditCard => PaymentRequest(creditCard, "rcjvm-store-account", 99))

  val serverHttpRequests = paymentRequests.map(paymentRequest =>
    HttpRequest(
      HttpMethods.POST,
      uri = Uri("/api/payments"),
      entity = HttpEntity(
        ContentTypes.`application/json`,
        paymentRequest.toJson.prettyPrint
      )
    )
  )

  Source(serverHttpRequests)
    .via(Http().outgoingConnection("localhost", 8080))
    .to(Sink.foreach[HttpResponse](println))
    .run()
}
