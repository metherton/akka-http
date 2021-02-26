package part4_client

import akka.actor.ActorSystem
import akka.http.scaladsl.{ConnectionContext, Http}
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpMethods, HttpRequest, HttpResponse, Uri}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import javax.net.ssl.SSLContext

import scala.util.{Failure, Success}
import spray.json._

object ConnectionLevel extends App with PaymentJsonProtocol {


  implicit val system = ActorSystem("ConnectionLevel")
  implicit val materializer = ActorMaterializer()
  import system.dispatcher
/*

  val httpsConnectionContext = ConnectionContext.https(SSLContext.getDefault)
  //            val bla = Http().newHostConnectionPoolHttps("https://financialmodelingprep.com/api/v3/quote/AAPL,FB?apikey=0a314c85fe75ed860b38f1d1b4c2bdd2", connectionContext = httpsConnectionContext)
  //            val bla = Http().newHostConnectionPoolHttps("https://financialmodelingprep.com/api/v3/profile/AAPL?apikey=demo", connectionContext = httpsConnectionContext)
  val connectionFlow = Http().newHostConnectionPoolHttps("financialmodelingprep.com", connectionContext = httpsConnectionContext)

  val cf = Http().outgoingConnection("financialmodelingprep.com")

  //akka.stream.scaladsl.Source.single(request).via(cf).to(Sink.foreach[HttpResponse](println)).run()
  def oneOfRequest(request: HttpRequest)=
  // akka.stream.scaladsl.Source.single(request).via(cf).to(Sink.foreach[HttpResponse](println)).run()
    akka.stream.scaladsl.Source.single(request).via(cf).runWith(Sink.head)
  //
  oneOfRequest(HttpRequest(uri = "/api/v3/profile/AAPL?apikey=0a314c85fe75ed860b38f1d1b4c2bdd2")).onComplete {
    case Success(response) => println(s"gor respon $response")
    case Failure(ex) => println(s"sedin failed: $ex")
  }


*/


//  val connectionFlow = Http().outgoingConnection("www.google.com")
  val connectionFlow = Http().outgoingConnection("financialmodelingprep.com")

  def oneOffRequest(request: HttpRequest) =
    Source.single(request).via(connectionFlow).runWith(Sink.head)

  oneOffRequest(HttpRequest(uri = "/api/v3/profile/AAPL?apikey=0a314c85fe75ed860b38f1d1b4c2bdd2")).onComplete {
    case Success(response) => println(s"Got successful response: $response")
    case Failure(ex) => println(s"sending the request failed; $ex")
  }

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
