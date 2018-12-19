package wasp

import com.fasterxml.jackson.core.`type`.TypeReference

import scala.concurrent.{ExecutionContext, Future}

sealed trait Response[T] {
  def map[K](fn: T => K):Response[K]
}
case class Success[T](config: T) extends Response[T] {
  override def map[K](fn: T=>K) = Success(fn(config))
}
case class Failure[T](reason: Any) extends Response[T] {
  override def map[K](fn: T => K): Response[K] = Failure[K](reason)
}

case class Path(value:String) {
  def append(more:Path) = Path(List(value, more.value).filterNot(_.isEmpty).mkString("."))
  override def toString = value
}

object Path {
  def apply(): Path = Path(value = "")
  def apply(components:String*): Path = Path(components.map(_.replaceAll("\\.", "\\.")).mkString("."))
  def from(value:String) = Path(value)
}

case class Health(status:String, uptime:Long) {
  def isHealthy = status == "OK"
}

case class Auth(userToken:String)

class Client(endpoint: String, basePath: Path = Path()) {
  val client = new HttpClient

  def get[T](clazz: Class[T]) (path: Path)(implicit context:ExecutionContext) = getJson(path).map(_.map(data => Json.fromJson(data, clazz)))
  def get[T](typeRef: TypeReference[T])(path: Path)(implicit context:ExecutionContext) = getJson(path).map(_.map(data => Json.fromJson(data, typeRef)))

  def getJson(path: Path)(implicit context:ExecutionContext) =
    client.get(endpoint + "/configuration", Map("path" -> basePath.append(path).toString))
      .map(response =>
        if(response.status == 200) Success(response.body)
        else Failure(response.body)
      )

  def getKeys(path: Path)(implicit context:ExecutionContext)  =
    client.get(endpoint + "/configuration/keys", Map("path" -> basePath.append(path).toString))
      .map(response =>
        if(response.status == 200) Success(Json.fromJson(response.body, classOf[List[String]]))
        else Failure(response.body)
      )

  def getReferences(path: Path)(implicit context: ExecutionContext) =
    client.get(endpoint + "/configuration/references", Map("path" -> basePath.append(path).toString))
        .map(response =>
          if(response.status == 200) Success(response.body)
          else Failure(response.body)
        )

  def add[T](path: Path, conf: T, auth:Auth)(implicit context:ExecutionContext) : Future[Response[String]] = add(path, Json.toJson(conf), auth)

  def add(path: Path, confJson: String, auth:Auth)(implicit context:ExecutionContext) : Future[Response[String]] =
      client.post(endpoint + "/configuration",  Map("path" -> basePath.append(path).toString, "token" -> auth.userToken), Some(confJson))
      .map(response => if (response.status == 200) Success("Added to path: " + path.toString) else Failure(Json.fromJson(response.body, classOf[Map[String, String]])))

  def addLink(path: Path, sourcePath: Path, auth: Auth)(implicit context: ExecutionContext) : Future[Response[String]] =
    client.post(endpoint + "/configuration/link", Map("path" -> basePath.append(path).toString, "token" -> auth.userToken), Some(sourcePath.toString))
      .map(response => if (response.status == 200) Success("Added link from path: " +path.toString+ " to path: " +sourcePath.toString) else Failure(Json.fromJson(response.body, classOf[Map[String, String]])))

  def delete(path: Path, auth:Auth)(implicit context:ExecutionContext)  =
      client.delete(endpoint + "/configuration", Map("path" -> basePath.append(path).toString, "token" -> auth.userToken))
      .map(response => if (response.status == 200) Success("Deleted path: " + path.toString) else Failure(Json.fromJson(response.body, classOf[Map[String, String]])))

  def healthCheck(implicit context: ExecutionContext) =
      client.get(endpoint + "/health").map(response => Json.fromJson(response.body, classOf[Health]))

  def shutdown = client.shutdown()
}

object Client {
  case class Config(waspHost: String = "localhost:9000", path: Path = Path(), method: String = "GET", value: Option[String] = None, authToken:Option[String] = None)

  def main(args:Array[String]) {
    import ExecutionContext.Implicits.global
    val methods = Set("GET", "ADD", "DELETE", "GET_KEYS", "ADD_LINK")

    val parser = new scopt.OptionParser[Config]("wasp-client") {
      head("scopt","3.x")
      opt[String]('h',"host") required() action { (x,c) => c.copy(waspHost = x) } text ("hostName:hostPort")
      opt[String]('p', "path") required() action { (x,c) => c.copy(path = Path.from(x)) }
      opt[String]('m', "method") required() action {(x,c) => c.copy(method = x)} validate {x =>if(methods contains x) success else failure("Not a valid method call to the API: " + x) }
      opt[String]('d', "data") action {(x,c) => c.copy(value = Some(x))}
      opt[String]('u', "authToken") action {(x,c) => c.copy(authToken = Some(x))}
      checkConfig { c =>
        if (c.method == "ADD" && c.value.isEmpty)
          failure("ADD called without data")
        else if (c.method == "ADD_LINK" && c.value.isEmpty)
          failure("ADD_LINK called without data")
        else if ((c.method == "ADD" || c.method == "DELETE" || c.method == "ADD_LINK") && c.authToken.isEmpty)
            failure("ADD/ADD_LINK/DELETE called without authToken")
        else
            success
      }
    }

    parser.parse(args, Config()).map {
      case Config(host,path,"GET",_, _) =>
        println(path)
        val client = new Client(host)
        client.get(classOf[Map[String,AnyRef]])(path) map {
          case Success(config) => config
          case Failure(reason) => reason
        } map {config => println(config)
          client.shutdown
        }

      case Config(host,path,"GET_KEYS",_, _) => {
        val client = new Client(host)
        client.getKeys(path).map{resp =>
          client.shutdown
        }

      }
      case Config(host,path,"ADD",value, Some(authToken)) => {
        val client = new Client(host)
        client.add(path,Json.fromJson(value.get,classOf[Any]), Auth(authToken)).map { resp =>
          client.shutdown
        }

      }
      case Config(host,path,"ADD_LINK",value, Some(authToken)) => {
        val client = new Client(host)
        client.addLink(path,Path.from(value.get), Auth(authToken)).map { resp =>
          client.shutdown
        }

      }
      case Config(host,path,"DELETE",_, authToken) => {
        val client: Client = new Client(host)
        client.delete(path, Auth(authToken.get)).map{ resp => client.shutdown
        }

      }
    } getOrElse {
      println(parser.usage)
    }
  }
}
