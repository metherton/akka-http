package part3_highlevelserver

import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpRequest, StatusCodes}
import akka.stream.ActorMaterializer

object DirectivesBreakdown extends App {

  implicit val system = ActorSystem("DirectivesBreakdown")
  implicit val materializer = ActorMaterializer()
  import system.dispatcher
  import akka.http.scaladsl.server.Directives._

  /*

      Type 1 - filtering directives

   */


  val simpleHttpMethodRoute =
    post { // equivalent directives for get, put, patch, option, delete, head
      complete(StatusCodes.Forbidden)
    }

  val simplePathRoute =
    path("about") {
      complete(
        HttpEntity(
          ContentTypes.`application/json`,
          """
            |<html>
            |<body>
            |hello from the about page
            |</body>
            |</html>
          """.stripMargin
        )
      )
    }

  val complexPathRoute =
    path("api" / "myEndpoint") { // api/myEndpoint // good way to do it
      complete(StatusCodes.OK)
    }

  val dontConfuse =
    path("api/myEndpoint") { // url encodes the string // not the right way
      complete(StatusCodes.OK)
    }

  val pathEndRoute =
    pathEndOrSingleSlash { // localhost:8080 OR localhost:8080/
      complete(StatusCodes.OK)
    }

 // Http().bindAndHandle(dontConfuse, "localhost", 8080)


  /*
      Type 2 - extraction directives

   */

  // GET on /api/item/42
  val pathExtractionRoute =
    path("api" / "item" / IntNumber) {(itemNumber: Int) =>
      // other directives

      println(s"I've got a number in my path: $itemNumber")
      complete(StatusCodes.OK)
    }

  // GET on /api/item/42
  val pathMultiExtractionRoute =
    path("api" / "order" / IntNumber / IntNumber) {(id, inventory) =>
      // other directives

      println(s"I've got two numbers in my path: $id and $inventory")
      complete(StatusCodes.OK)
    }



  /*
      Type 3 - parameter directives
   */

  val queryParamExtractionRoute =
    // /api/item?id=45
    path("api" / "item") {
      parameter('id.as[Int]) { (itemId: Int) =>
        println(s"I've extracted the id as $itemId")
        complete(StatusCodes.OK)
      }
    }

  val extractRequestRoute =
    path("controlEndpoint") {
      extractRequest { (httpRequest: HttpRequest) =>
        extractLog { (log: LoggingAdapter) =>
          log.info(s"I got the http request: $httpRequest")
          complete(StatusCodes.OK)
        }
      }
    }

  Http().bindAndHandle(extractRequestRoute, "localhost", 8080)
}
