package part3_highlevelserver

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer

object HighLevelIntro extends App {


  implicit val system = ActorSystem("HighLevelIntro")
  implicit val materializer = ActorMaterializer()
  import system.dispatcher

  // directives

  import akka.http.scaladsl.server.Directives._

  val simpleRoute: Route =
    path("home") { // DIRECTIVE
      complete(StatusCodes.OK) // DIRECTIVE
    }

  val pathGetRoute: Route =
    path("home") { // DIRECTIVE
      get {
        complete(StatusCodes.OK) // DIRECTIVE
      }
    }

  // chaining directives with ~
  val chainedRoute: Route = {
    path("myEndpoint") {
      get {
        complete(StatusCodes.OK)
      } ~ // VERY IMPORTANT
      post {
        complete(StatusCodes.Forbidden)
      }
    } ~
    path("home") {
      complete {
        HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          """
            |<html>
            |<body>
            |hello from the high level Akka HTTP
            |</body>
            |</html>
          """.stripMargin
        )
      }
    }
  } // ROUTING TREE



  Http().bindAndHandle(pathGetRoute, "localhost", 8080)

}
