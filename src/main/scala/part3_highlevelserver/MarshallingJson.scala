package part3_highlevelserver

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.http.scaladsl.{ConnectionContext, Http, model}
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.{HttpHeader, HttpRequest, HttpResponse, StatusCodes}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import javax.net.ssl.SSLContext
import part3_highlevelserver.GameAreaMap.AddPlayer
import part4_client.ConnectionLevel.{connectionFlow, system}
import part4_client.StockNews

import scala.concurrent.Future
import scala.util.{Failure, Success}
// step 1
import spray.json._
case class Player(nickname: String, characterClass: String, level: Int)
case class Team(players: List[Player])
case class Stock(symbol: String, name: String, exchange: String = "default", price: Double)
case class Stocks(stocks: List[Stock])
case class StockNews(symbol: String, publishedDate: String, title: String, image: String, site: String, text: String, url:String)

trait PlayerJsonProtocol extends DefaultJsonProtocol {
  // step 2
  implicit val playerFormat = jsonFormat3(Player)
  implicit  val teamFormat = jsonFormat1(Team)
  implicit val stockFormat = jsonFormat4(Stock)
  implicit val stocksFormat = jsonFormat1(Stocks)
  implicit val stockNewsJson = jsonFormat7(StockNews)
}

object GameAreaMap {
  case object GetAllPlayers
  case class GetPlayer(nickname: String)
  case class GetPlayersByClass(characterClass: String)
  case class AddPlayer(player: Player)
  case class RemovePlayer(player: Player)
  case object OperationSuccess
  case object GetStocks
}



class GameAreaMap extends Actor with ActorLogging with PlayerJsonProtocol {

  implicit val system = ActorSystem("GaneAreaNao")
  implicit val materializer = ActorMaterializer()
  import GameAreaMap._
  import scala.concurrent.duration._
  import system.dispatcher

  var players = Map[String, Player]()

  override def receive: Receive = {
    case GetAllPlayers =>
      log.info("Getting all players")
      sender() ! players.values.toList
    case GetPlayer(nickname) =>
      log.info(s"getting player with nickname: $nickname")
      sender() ! players.get(nickname)
    case GetPlayersByClass(characterClass) =>
      log.info(s"Getting all players with character class: $characterClass")
      val b = players.values.toList.filter(_.characterClass == characterClass)
      sender() ! b
    case AddPlayer(player: Player) =>
      log.info(s"trying to add player: $player")
      players = players + (player.nickname -> player)
      sender() ! OperationSuccess
    case RemovePlayer(player: Player) =>
      log.info(s"trying to remove player: $player")
      players = players - player.nickname
      sender() ! OperationSuccess
    case GetStocks =>
      log.info("Getting all stocks")
      val httpsConnectionContext = ConnectionContext.https(SSLContext.getDefault)
      val connectionFlow = Http().outgoingConnectionHttps("financialmodelingprep.com", 443, httpsConnectionContext)


      def oneOffRequest(request: HttpRequest) =
        Source.single(request).via(connectionFlow).runWith(Sink.head)

      val httpResponseFuture: Future[HttpResponse] = oneOffRequest(HttpRequest(uri = "/api/v3/stock_news?tickers=AAPL,FB,GOOG,AMZN&limit=50&&apikey=0a314c85fe75ed860b38f1d1b4c2bdd2"))

//      val entityFuture = httpResponseFuture.map(_.entity)
//      val listStockNews = entityFuture.flatMap(entity => entity.toStrict(10 seconds)).map(d => d.data.utf8String.parseJson.convertTo[List[StockNews]])

      httpResponseFuture.onComplete {
        case Success(response) => sender() ! response
        case Failure(_) => println("failed")
      }

     // sender() ! listStockNews
//        .onComplete {
//        case Success(response) =>
//          val strictEntityFuture = response.entity.toStrict(10 seconds)
//          val stocksFuture = strictEntityFuture.map(_.data.utf8String.parseJson.convertTo[List[StockNews]])
//
//          stocksFuture.onComplete {
//            case Success(x) =>
//              println(s"entity string: $x")
//              sender() ! x
//            case Failure(x) =>
//              println(s"failed with $x")
//          }
//          println(s"Got successful response entity: ${response.entity}")
//        case Failure(ex) => println(s"sending the request failed; $ex")
 //     }

      //sender() ! players.values.toList

  }
}



//implicit object ListPlayerJsonProtocol extends RootJsonFormat[DockerApiResult] {
//  def read(value: JsValue) = DockerApiResult(value.convertTo[List[Container]])
//  def write(obj: DockerApiResult) = obj.results.toJson
//}

object MarshallingJson extends App
  // step 3
  with PlayerJsonProtocol
  // step 4
  with SprayJsonSupport {

  implicit val system = ActorSystem("MarshallingJson")
  implicit val materializer = ActorMaterializer()
  import system.dispatcher
  import GameAreaMap._
  import akka.http.scaladsl.server.Directives._
  import scala.concurrent.duration._
  import akka.pattern.ask
  import akka.util.Timeout

  val rtjvmGameMap = system.actorOf(Props[GameAreaMap], "rockTheJVMGameAreaMap")
  val playersList = List(
    Player("martin_kilz_u", "Warrior", 70),
    Player("rolandbravehart007", "Elf", 67),
    Player("daniel_rock03", "Wizard", 30)
  )
  playersList.foreach { player =>
    rtjvmGameMap ! AddPlayer(player)
  }

  /*
        GET /api/player, returns all players in map as json
        GET /api/player/nickname, returns player with nickname as json
        GET /api/player?nickname=X, does the same as json
        GET /api/player/class/(charClass) , returns all players with given class
        POST /api/player with JSON payload, adds the player to the map
        Exercise DELETE /api/player with JSON payload, removes player from map


   */

  implicit val timeout = Timeout(10 seconds)
  val rtjvmGameRouteSkel =
    pathPrefix("api" / "player") {
      get {
        path("class" / Segment) { characterClass =>
          val playersByClassFuture = (rtjvmGameMap ? GetPlayersByClass(characterClass)).mapTo[List[Player]]
          complete(playersByClassFuture)
        } ~
        (path(Segment) | parameter("nickname")) { nickname =>
          val playerOptionFuture = (rtjvmGameMap ? GetPlayer(nickname)).mapTo[Option[Player]]
          complete(playerOptionFuture)
        } ~
        pathEndOrSingleSlash {
          val bla: Future[Any] = (rtjvmGameMap ? GetAllPlayers)
          complete(bla.mapTo[List[Player]])
        }
      } ~
      post {
        entity(as[Player]) { player =>
          println(s"player $player")
          complete((rtjvmGameMap ? AddPlayer(player)).map(_ => StatusCodes.OK))
        }
      } ~
      delete {
        entity(as[Player]) { player =>
          complete((rtjvmGameMap ? RemovePlayer(player)).map(_ => StatusCodes.OK))
        }

      }
    } ~
    pathPrefix("api" / "bla") {
      get {
        pathEndOrSingleSlash {
          val httpsConnectionContext = ConnectionContext.https(SSLContext.getDefault)
          val connectionFlow = Http().outgoingConnectionHttps("financialmodelingprep.com", 443, httpsConnectionContext)


          def oneOffRequest(request: HttpRequest) =
            Source.single(request).via(connectionFlow).runWith(Sink.head)

          onComplete(oneOffRequest(HttpRequest(uri = "/api/v3/stock_news?tickers=AAPL,FB,GOOG,AMZN&limit=50&&apikey=0a314c85fe75ed860b38f1d1b4c2bdd2"))) {
            case Success(response) =>
              val strictEntityFuture = response.entity.toStrict(10 seconds)
              val stocksFuture = strictEntityFuture.map(_.data.utf8String.parseJson.convertTo[List[StockNews]])

              onComplete(stocksFuture) {
                case Success(x) => complete(x)
                case Failure(x) => complete(StatusCodes.BadRequest)
              }

            case Failure(ex) => complete(StatusCodes.BadRequest)
          }
        }
      } ~
      post {
        entity(as[Team]) { team =>
          val listPlayers = team.players
          println(s"players is $listPlayers.")
          complete(StatusCodes.OK)
        }
      }
    }

  Http().bindAndHandle(rtjvmGameRouteSkel, "localhost", 8080)
}
