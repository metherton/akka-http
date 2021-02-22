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

  /*
      Type 3 - composite directives

   */
  val simpleNestedRoute =
    path("api" / "item") {
      get {
        complete(StatusCodes.OK)
      }
    }

  val compactSimpleNestedRoute = (path("api" / "item") & get) {
    complete(StatusCodes.OK)
  }

  val compactExtractRequestRoute =
    (path("controlEndpoint") & extractRequest & extractLog) { (request, log) =>
      log.info(s"I got the request: $request")
      complete(StatusCodes.OK)
    }

  // /about and /aboutUs
  val repeatedRoute =
    path("about") {
      complete(StatusCodes.OK)
    } ~
    path("aboutUs") {
      complete(StatusCodes.OK)
    }

  val dryRoute =
    (path("about") | path("aboutUs")) {
      complete(StatusCodes.OK)
    }

  // yourblog.com/42 AND yourblog.com?postId=42
  val blogByIdRoute =
    path(IntNumber) {(blogId: Int) =>
      // complex server logic
      complete(StatusCodes.OK)

    }

  val blogByQueryParamRoute =
    parameter('postId.as[Int]) {(blogpostId: Int) =>
      // the same server logic
      complete(StatusCodes.OK)
    }

  val combinedBlogByIdRoute =
    (path(IntNumber) | parameter('postId.as[Int])) {(blogpostId: Int) =>
      // your original server logic
      complete(StatusCodes.OK)
    }

  /*
      Type 4 - "actionable" directives

   */

  val completeOkRoute = complete(StatusCodes.OK)

  val failedRoute =
    path("notsupported") {
      failWith(new RuntimeException("unsupported")) // complete with http 500
    }

  val routeWithRejection =
//    path("home") {
//      reject
//    } ~
    path("index") {
      completeOkRoute
    }

  /*
      Exercise: Can you spot the mistake ?

   */
  val getOrPutPath =
    path("api" / "myEndpoint") {
      get {
        completeOkRoute
      } ~ // this tilda was missing so added it
      post {
        complete(StatusCodes.Forbidden)
      }
    }


  Http().bindAndHandle(getOrPutPath, "localhost", 8080)
}
