package part2_lowlevelserver

import java.io.InputStream
import java.security.{KeyStore, SecureRandom}

import akka.actor.ActorSystem
import akka.http.scaladsl.{ConnectionContext, Http, HttpsConnectionContext}
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpMethods, HttpRequest, HttpResponse, StatusCodes, Uri}
import akka.stream.ActorMaterializer
import javax.net.ssl.{KeyManagerFactory, SSLContext, TrustManagerFactory}
import part2_lowlevelserver.GuitarDB.{AddQuantity, CreateGuitar, FindGuitarsInStock, GuitarCreated}
import part2_lowlevelserver.LowLevelHttps.getClass
import part2_lowlevelserver.LowLevelRest.{getGuitar, guitarDb}

import scala.concurrent.Future

object HttpsContext {

  // STEP 1: key store
  val ks: KeyStore = KeyStore.getInstance("PKCS12")
  val keystoreFile: InputStream = getClass.getClassLoader.getResourceAsStream("keystore.pkcs12")
  // alternative: new FileInputStream(new File("src/main/resources/keystore.pkcs12"))

  val password = "akka-https".toCharArray // fetch the password from a secure place
  ks.load(keystoreFile, password)

  // STEP 2: initialize a key manager
  val keyManagerFactory = KeyManagerFactory.getInstance("SunX509") // PKI Public key infrastructure
  keyManagerFactory.init(ks, password)

  // STEP 3: initialize a trust manager
  val trustManagerFactory = TrustManagerFactory.getInstance("SunX509")
  trustManagerFactory.init(ks)

  // STEP 4 - initialize a SSL context
  val sslContext: SSLContext = SSLContext.getInstance("TLS")
  sslContext.init(keyManagerFactory.getKeyManagers, trustManagerFactory.getTrustManagers, new SecureRandom)

  // STEP 7 - return the HTTP connection context
  val httpsConnectionContext: HttpsConnectionContext = ConnectionContext.https(sslContext)

}

object LowLevelHttps extends App {

  implicit val system = ActorSystem("LowLevelHttps")
  implicit val materializer = ActorMaterializer()


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

  val httpsBinding = Http().bindAndHandleSync(requestHandler, "localhost", 8443, HttpsContext.httpsConnectionContext )

}
