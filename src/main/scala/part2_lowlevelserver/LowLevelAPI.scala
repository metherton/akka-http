package part2_lowlevelserver

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.IncomingConnection
import akka.http.scaladsl.model.headers.Location
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpMethods, HttpRequest, HttpResponse, StatusCodes, Uri}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Sink}

import scala.concurrent.Future
import scala.util.{Failure, Success}
import scala.concurrent.duration._


object LowLevelAPI extends App {

  implicit val system = ActorSystem("LowLevelServerAPI")
  implicit val materializer = ActorMaterializer()
  import system.dispatcher

  val serverSource = Http().bind("localhost", 8000)
  val connectionSink = Sink.foreach[IncomingConnection] { connection =>
    println(s"Accepted incoming connection from ${connection.remoteAddress}")
  }

  val serverBindingFuture = serverSource.to(connectionSink).run()
  serverBindingFuture.onComplete {
    case Success(binding) =>
      println("server binding successful")
//      binding.unbind()
      binding.terminate(2 seconds)
    case Failure(ex) => println(s"server binding failed: $ex")
  }


  /*
      method 1 - synchronously
   */

  val requestHandler: HttpRequest => HttpResponse = {
    case HttpRequest(HttpMethods.GET, _, _, _, _) =>
      HttpResponse(
        StatusCodes.OK, // http 200
        entity = HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          """
            |<html>
            |<body>
            |hello from akka
            |</body>
            |</html>
          """.stripMargin
        )
      )



    case request: HttpRequest =>
      request.discardEntityBytes()
      HttpResponse(
        StatusCodes.NotFound, // 404
        entity = HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          """
            |<html>
            |<body>
            |OOPS the resource can't be found
            |</body>
            |</html>
          """.stripMargin

        )
      )
  }

  val httpSyncConnectionHandler = Sink.foreach[IncomingConnection] { connection =>
    connection.handleWithSyncHandler(requestHandler)
  }

 // Http().bind("localhost",8080).runWith(httpSyncConnectionHandler)

  // shorthand version
  //Http().bindAndHandleSync(requestHandler, "localhost", 8080)

  /*
      Method 2: serve back HTTP response ASYNCHRONOUSLY

   */

  val asyncRequestHandler: HttpRequest => Future[HttpResponse] = {
    case HttpRequest(HttpMethods.GET, Uri.Path("/home"), _, _, _) => // method, URI, HTTP headers, content and the protocol (HTTP1.1/HTTP2.0)
      Future(HttpResponse(
        StatusCodes.OK, // http 200
        entity = HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          """
            |<html>
            |<body>
            |hello from akka
            |</body>
            |</html>
          """.stripMargin
        )
      ))

    case request: HttpRequest =>
      request.discardEntityBytes()
      Future(HttpResponse(
        StatusCodes.NotFound, // 404
        entity = HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          """
            |<html>
            |<body>
            |OOPS the resource can't be found
            |</body>
            |</html>
          """.stripMargin

        )
      ))
  }


  val httpAsyncConnectionHandler = Sink.foreach[IncomingConnection] { connection =>
    connection.handleWithAsyncHandler(asyncRequestHandler)
  }

  // streams based "manual" version
//  Http().bind("localhost", 8081).runWith(httpAsyncConnectionHandler)

  // shorthand version
  Http().bindAndHandleAsync(asyncRequestHandler, "localhost", 8081)


  /*
      Method 3 - async via akka streams

   */

  val streamsBasedRequestHandler: Flow[HttpRequest, HttpResponse, _] = Flow[HttpRequest].map {
    case HttpRequest(HttpMethods.GET, Uri.Path("/home"), _, _, _) => // method, URI, HTTP headers, content and the protocol (HTTP1.1/HTTP2.0)
      HttpResponse(
        StatusCodes.OK, // http 200
        entity = HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          """
            |<html>
            |<body>
            |hello from akka
            |</body>
            |</html>
          """.stripMargin
        )
      )

    case request: HttpRequest =>
      request.discardEntityBytes()
      HttpResponse(
        StatusCodes.NotFound, // 404
        entity = HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          """
            |<html>
            |<body>
            |OOPS the resource can't be found
            |</body>
            |</html>
          """.stripMargin

        )
      )
  }

  // manual version
//  Http().bind("localhost", 8082).runForeach { connection =>
//    connection.handleWith(streamsBasedRequestHandler)
//  }

  // shorthand
  Http().bindAndHandle(streamsBasedRequestHandler, "localhost", 8082)


  /*
      Exercise: create your own HTTP server running on localhost on 8388 , which replies
      - with a welcome message on the front door localhost:8388
      - with a proper HTML on localhost:8388/about
      - with a 404 message otherwise
   */

  val exerciseBasedRequestHandler: Flow[HttpRequest, HttpResponse, _] = Flow[HttpRequest].map {

    case HttpRequest(HttpMethods.GET, Uri.Path("/about"), _, _, _) => // method, URI, HTTP headers, content and the protocol (HTTP1.1/HTTP2.0)
      HttpResponse(
        StatusCodes.OK, // http 200
        entity = HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          """
            |<html>
            |<body>
            |about me
            |</body>
            |</html>
          """.stripMargin
        )
      )

    // path /search redirects to some other part of our app / web / microservice
    case HttpRequest(HttpMethods.GET, Uri.Path("/search"), _, _, _) =>
      HttpResponse(
        StatusCodes.Found, // http 302
        headers = List(Location("http://google.com"))
      )

    case HttpRequest(HttpMethods.GET, Uri.Path("/"), _, _, _) => // method, URI, HTTP headers, content and the protocol (HTTP1.1/HTTP2.0)
      HttpResponse(
        StatusCodes.OK, // http 200
        entity = HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          """
            |<html>
            |<body>
            |welcome to the front door
            |</body>
            |</html>
          """.stripMargin
        )
      )

    case request: HttpRequest =>
      request.discardEntityBytes()
      HttpResponse(
        StatusCodes.NotFound, // 404
        entity = HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          """
            |<html>
            |<body>
            |404 - OOPS the resource can't be found
            |</body>
            |</html>
          """.stripMargin

        )
      )
  }

  Http().bindAndHandle(exerciseBasedRequestHandler, "localhost", 8388)

  // shutdown the server
//  val bindingFuture = Http().bindAndHandle(exerciseBasedRequestHandler, "localhost", 8388)
//  bindingFuture.flatMap(binding => binding.unbind())
//    .onComplete(_ => system.terminate())





}
