package part2_lowlevelserver

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer

object LowLevelAPI extends App {

  implicit val system = ActorSystem("LowLevelServerAPI")
  implicit val materializer = ActorMaterializer()

}
