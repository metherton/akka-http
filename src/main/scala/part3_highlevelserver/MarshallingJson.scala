package part3_highlevelserver

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.stream.ActorMaterializer
import part3_highlevelserver.GameAreaMap.AddPlayer

case class Player(nickname: String, characterClass: String, level: Int)

object GameAreaMap {
  case object GetAllPlayers
  case class GetPlayer(nickname: String)
  case class GetPlayersByClass(characterClass: String)
  case class AddPlayer(player: Player)
  case class RemovePlayer(player: Player)
  case object OperationSuccess
}

class GameAreaMap extends Actor with ActorLogging {

  import GameAreaMap._

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
      sender() ! players.values.toList.filter(_.characterClass == characterClass)
    case AddPlayer(player: Player) =>
      log.info(s"trying to add player: $player")
      players = players + (player.nickname -> player)
      sender() ! OperationSuccess
    case RemovePlayer(player: Player) =>
      log.info(s"trying to remove player: $player")
      players = players - player.nickname
      sender() ! OperationSuccess


  }
}

object MarshallingJson extends App {

  implicit val system = ActorSystem("MarshallingJson")
  implicit val materializer = ActorMaterializer()
  import system.dispatcher
  import GameAreaMap._
  import akka.http.scaladsl.server.Directives._

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

  val rtjvmGameRouteSkel =
    pathPrefix("api" / "player") {
      get {
        path("class" / Segment) { characterClass =>
          // TODO 1: get all players with character class
          reject
        } ~
        (path(Segment) | parameter("nickname")) { nickname =>
          // TODO 2: get  player with nickname
          reject
        } ~
        pathEndOrSingleSlash {
          // TODO 3: get all the players
          reject
        }
      } ~
      post {
        // TODO 4: add a player
        reject
      } ~
      delete {
        // TODO 5: (exercise) delete a player
        reject
      }
    }

}
