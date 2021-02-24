package part3_highlevelserver

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.stream.ActorMaterializer
import part2_lowlevelserver.LowLevelRest.requestHandler

import scala.concurrent.duration._
import spray.json._

import scala.util.{Failure, Success}

case class Person(pin: Int, name: String)

trait PersonJsonProtocol extends DefaultJsonProtocol {
  implicit val personJson = jsonFormat2(Person)
}

object HighLevelExercise extends App with PersonJsonProtocol {

  implicit val system = ActorSystem("HighLevelExercise")
  implicit val materializer = ActorMaterializer()

  import system.dispatcher
  import akka.http.scaladsl.server.Directives._

  /*
      Exercise:
      GET /api/people: retrieves all people
      GET /api/people/pin retrieve the person with that pin, return as JSON
      GET /api/people?pin=X (same)
      harder - POST /api/people with a JSON payload denoting a Person, add that person to your database
      - extract the HTTP requests payload (entity)
      - extract the request
      - process the entitys data
      /// see low level rest server ... strict
   */

  var people = List(
    Person(1, "Alice"),
    Person(2, "Bob"),
    Person(3, "Charlie")
  )

  def toHttpEntity(payload: String) = HttpEntity(ContentTypes.`application/json`, payload)

  val personServerRoute =
    pathPrefix("api" / "people") {
      get {
        (path(IntNumber) | parameter('pin.as[Int])) { pin =>
          // TODO: fetch person with pin
          complete(HttpEntity(
            ContentTypes.`application/json`,
            people.find(_.pin == pin).headOption.toJson.prettyPrint
          ))
        } ~
        pathEndOrSingleSlash {
          // TODO: fetch all the people
          complete(HttpEntity(
            ContentTypes.`application/json`,
            people.toJson.prettyPrint
          ))
        }
      } ~
      (post & pathEndOrSingleSlash & extractRequest & extractLog) { (request, log) =>

        // TODO: insert a person into database
        val entity = request.entity
        val strictEntityFuture = entity.toStrict(2 seconds)
        val personFuture = strictEntityFuture.map(_.data.utf8String.parseJson.convertTo[Person])
        onComplete(personFuture) {
          case Success(person) =>
            log.info(s"Got person: $person")
            people = people :+ person
            complete(StatusCodes.OK)
          case Failure(ex) =>
            failWith(ex)
        }
        // SIDE EFFECT
//        personFuture.onComplete {
//          case Success(person) =>
//            log.info(s"Got person: $person")
//            people = people :+ person
//          case Failure(ex) =>
//            log.warning(s"something failed with fetching the person from the entity: $ex")
//        }
//        complete(personFuture
//          .map(_ => StatusCodes.OK)
//          .recover {
//            case _ => StatusCodes.InternalServerError
//        })
      }
    }
  Http().bindAndHandle(personServerRoute, "localhost", 8080)
}
