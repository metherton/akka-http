package part2_lowlevelserver

import akka.pattern.ask
import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpMethods, HttpRequest, HttpResponse, StatusCodes, Uri}
import akka.stream.ActorMaterializer
import akka.util.Timeout
import part2_lowlevelserver.GuitarDB.{AddQuantity, CreateGuitar, FindAllGuitars, FindGuitar, FindGuitarsInStock, GuitarCreated}

import scala.concurrent.duration._
import scala.concurrent.Future

// step 1
import spray.json._

case class Guitar(make: String, model: String, quantity: Int = 0)

object GuitarDB {
  case class CreateGuitar(guitar: Guitar)
  case class GuitarCreated(id: Int)
  case class FindGuitar(id: Int)
  case object FindAllGuitars
  case class AddQuantity(id: Int, quantity: Int)
  case class FindGuitarsInStock(inStock: Boolean)
}

class GuitarDB extends Actor with ActorLogging {

  import GuitarDB._

  var guitars: Map[Int, Guitar] = Map()
  var currentGuitarId: Int = 0

  override def receive: Receive = {
    case FindAllGuitars =>
      log.info("searching for all guitars")
      sender() ! guitars.values.toList
    case FindGuitar(id) =>
      log.info(s"searching guitar by id: $id")
      sender() ! guitars.get(id)
    case CreateGuitar(guitar) =>
      log.info(s"Adding guitar with id $currentGuitarId")
      guitars = guitars + (currentGuitarId -> guitar)
      sender() ! GuitarCreated(currentGuitarId)
      currentGuitarId += 1
    case AddQuantity(id, quantity) =>
      log.info(s"Trying to add $quantity items for guitar $id")
      val guitar: Option[Guitar] = guitars.get(id)
      val newGuitar: Option[Guitar] = guitar.map {
        case Guitar(make, model, q) => Guitar(make, model, q + quantity)
      }
      newGuitar.foreach(guitar => guitars = guitars + (id -> guitar))
      sender() ! newGuitar
    case FindGuitarsInStock(inStock) =>
      log.info(s"searching for all guitars ${if(inStock) "in"  else "out of" } stock")
      if (inStock)
        sender() ! guitars.values.filter(_.quantity > 0)
      else
        sender() ! guitars.values.filter(_.quantity == 0)
  }
}

//step 2
trait GuitarStoreJsonProtocol extends DefaultJsonProtocol {
  // step 3
  implicit val guitarFormat = jsonFormat3(Guitar)
}


object LowLevelRest extends App with GuitarStoreJsonProtocol {

  implicit val system = ActorSystem("LowLevelRest")
  implicit val materializer = ActorMaterializer()
  import system.dispatcher

  /*
      GET on localhost:8080/api/guitar => all the guitars in the store
      GET on localhost:8080/api/guitar?id=X => fetches guitar associated with id X
      POST on localhost:8080/api/guitar => insert the guitar into the store
   */

  // JSON => Marshalling
  val simpleGuitar = Guitar("Fender", "Stratocaster")
  println(simpleGuitar.toJson.prettyPrint)

  // unmarshalling
  val simpleGuitarJsonString =
    """
      |{
      |  "make": "Fender",
      |  "model": "Stratocaster",
      |  "quantity": 3
      |}
    """.stripMargin

  println(simpleGuitarJsonString.parseJson.convertTo[Guitar])

  /*

      set up
   */
  val guitarDb = system.actorOf(Props[GuitarDB], "LowLevelGuitarDB")
  val guitarList = List(
    Guitar("Fender", "Stratocaster"),
    Guitar("Gibson", "Les Paul"),
    Guitar("Martin", "LX1")
  )
  guitarList.foreach { guitar =>
     guitarDb ! CreateGuitar(guitar)
  }

  /*

      Server code
   */

  implicit val defaultTimeout = Timeout(2 seconds)

  def getGuitar(query: Query): Future[HttpResponse] = {
    val guitarId = query.get("id").map(_.toInt) // Option[Int]
    guitarId match {
      case None => Future(HttpResponse(StatusCodes.NotFound)) // api/guitar?id=
      case Some(id: Int) =>
        val guitarFuture: Future[Option[Guitar]] = (guitarDb ? FindGuitar(id)).mapTo[Option[Guitar]]
        guitarFuture.map {
          case None => HttpResponse(StatusCodes.NotFound) // api/guitar?id=9000
          case Some(guitar) =>
            HttpResponse(
              entity = HttpEntity(
                ContentTypes.`application/json`,
                guitar.toJson.prettyPrint
              )
            )
        }
    }
  }

//  def getInventory(query: Query): Future[HttpResponse] = {
//    val inStock = query.get("inStock").map(_.toBoolean) // Option[Int]
//    inStock match {
//      case None => Future(HttpResponse(StatusCodes.NotFound)) // api/guitar?inStock=
//      case Some(inStock: Boolean) =>
//        val guitarFutures: Future[List[Guitar]] = (guitarDb ? FindAllGuitars()).mapTo[List[Guitar]]
//        guitarFutures.filter((guitar) => {if (inStock) guitar.quantit}).map {
//
//        }
//    }
//  }


  val requestHandler: HttpRequest => Future[HttpResponse] = {
    case HttpRequest(HttpMethods.POST, uri@Uri.Path("/api/guitar/inventory"),_, _, _) =>
      val query = uri.query()
      val guitarId: Option[Int] = query.get("id").map(_.toInt) // Option[Int]
      val guitarQuantity: Option[Int] = query.get("quantity").map(_.toInt) // Option[Int]
      val validGuitarResponseFuture: Option[Future[HttpResponse]] = for {
        id <- guitarId
        quantity <- guitarQuantity
      } yield {
        val newGuitarFuture: Future[Option[Guitar]] = (guitarDb ? AddQuantity(id, quantity)).mapTo[Option[Guitar]]
        newGuitarFuture.map(_ => HttpResponse(StatusCodes.OK))
      }
      validGuitarResponseFuture.getOrElse(Future(HttpResponse(StatusCodes.BadRequest)))
    case HttpRequest(HttpMethods.GET, uri@Uri.Path("/api/guitar/inventory"),_, _, _) =>
      /*
          Query parameter handling code
          localhost:8080/api/endpoint?param1=value1&param2=value2
       */

      val query = uri.query() // query object <=> Map[String, String]
      val inStockOption = query.get("inStock").map(_.toBoolean) // Option[Boolean]

      inStockOption match {
        case Some(inStock) =>
          val guitarsFuture: Future[List[Guitar]] = (guitarDb ? FindGuitarsInStock(inStock)).mapTo[List[Guitar]]
          guitarsFuture.map { guitars =>
            HttpResponse(
              entity = HttpEntity(
                ContentTypes.`application/json`,
                guitars.toJson.prettyPrint
              )
            )
          }
        case None => Future(HttpResponse(StatusCodes.BadRequest))
      }



//      getInventory(query)
    case HttpRequest(HttpMethods.GET, uri@Uri.Path("/api/guitar"),_, _, _) =>
      /*
          Query parameter handling code
          localhost:8080/api/endpoint?param1=value1&param2=value2
       */

      val query = uri.query() // query object <=> Map[String, String]
      if (query.isEmpty) {
        val guitarsFuture: Future[List[Guitar]] = (guitarDb ? FindAllGuitars).mapTo[List[Guitar]]
        guitarsFuture.map { guitars =>
          HttpResponse(
            entity = HttpEntity(
              ContentTypes.`application/json`,
              guitars.toJson.prettyPrint
            )
          )
        }
      } else {
        // fetch guitar associated to the guitar id
        // localhost:8080/api/guitars?id=5
        getGuitar(query)
      }

    case HttpRequest(HttpMethods.POST, Uri.Path("/api/guitar"), _, entity, _) =>
      // entities are a Source[ByteString]
      val strictEntityFuture = entity.toStrict(3 seconds)
      strictEntityFuture.flatMap { strictEntity =>
        val guitarJsonString = strictEntity.data.utf8String
        val guitar = guitarJsonString.parseJson.convertTo[Guitar]
        val guitarCreatedFuture: Future[GuitarCreated] = (guitarDb ? CreateGuitar(guitar)).mapTo[GuitarCreated]
        guitarCreatedFuture.map { _ =>
          HttpResponse(StatusCodes.OK)
        }

      }
    case request: HttpRequest =>
      request.discardEntityBytes()
      Future {
        HttpResponse(status = StatusCodes.NotFound)
      }
  }

  Http().bindAndHandleAsync(requestHandler, "localhost", 8080)

  /*
      Exercise: enhance the Guitar case class with a quantity field, by default 0
      - GET to /api/guitar/inventory?inStock=true/false which the guitars in/out of stock as JSON
      - POST to /api/guitar/inventory?id=X&quantity=Y which adds Y guitars to the stock for the guitar with id X

   */

}
