package controllers

import akka.pattern.AskSupport
import akka.persistence.{SaveSnapshotFailure, SaveSnapshotSuccess}
import domain.{Global, History}
import models.{SnapshotSaveException, _}
import org.joda.time.DateTime
import play.api.Play.current
import play.api.libs.ws.WS
import play.api.mvc.{Action, Controller, Result}
import play.api.{Logger, Play, Configuration => PlayConfiguration}
import security.{AuthAction, AuthenticatorService}
import utils.JsonUtils

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
case class StatusResponse(path: String,message: String)
case class ErrorResponseWithPath(path: String,message: String)
case class ErrorResponse(message: String)

object ConfigurationController extends Controller with AskSupport {
  import Global.{actorSystem, askTimeout, configuration}

  implicit val config: PlayConfiguration = Play.current.configuration
  implicit val authenticationService : AuthenticatorService = AuthenticatorService.instance(config)

  def sendResponseWithHeader(value: Any, nodeMeta: NodeMeta) = {
    Ok(JsonUtils.toJson(value, pretty = true))
      .withHeaders(("x-Modified-By", nodeMeta.getAuthor), ("x-Modified-At", nodeMeta.modifiedAt))
  }

  def get(path: String, withDefaults: Boolean) = Action.async { request =>
    (configuration ? FindConfig(Path.from(path), withDefaults)).map {
      case Some((value, nodeMeta: NodeMeta)) => sendResponseWithHeader(value, nodeMeta)
      case _ => NotFound(JsonUtils.toJson(ErrorResponseWithPath(path, "Not found")))
    }
  }

  def getReferences(path: String) = Action.async { request =>
    (configuration ? FindReferences(Path.from(path))).map {
      case Some((value, nodeMeta: NodeMeta)) => sendResponseWithHeader(value, nodeMeta)
      case _ => NotFound(JsonUtils.toJson(ErrorResponseWithPath(path, "Not found")))
    }
  }

  def keys(path: String) = Action.async {
    (configuration ? FindKeys(Path.from(path))).map {
      case Some((list : List[Any], nodeMeta: NodeMeta)) => sendResponseWithHeader(list, nodeMeta)
      case _ => NotFound(JsonUtils.toJson(ErrorResponseWithPath(path, "No keys found. Path must lead to a Namespace.")))
    }
  }

  def getDefaults(path: String) = Action.async {
    (configuration ? FindDefaults(Path.from(path))).map {
      case Some((defaults, nodeMeta: NodeMeta)) => sendResponseWithHeader(defaults, nodeMeta)
      case _ => NotFound(JsonUtils.toJson(ErrorResponseWithPath(path, "No Defaults found.")))
    }
  }

  def history(path:Option[String]) = Action.async {
    (path match {
      case Some(pathExpr) => new History(actorSystem).history(Path.from(pathExpr))
      case None => new History(actorSystem).history()
    }).map {
      case list: List[Any] => Ok(JsonUtils.toJson(list))
      case x => NotFound(JsonUtils.toJson(ErrorResponse("Unknown value: " + x)))
    }
  }

  def restore(seqNo: Long, path: String) = Action.async {
    new History(actorSystem).restore(seqNo, Path.from(path)).map {
      case Some(node: Any) => Ok(JsonUtils.toJson(node))
      case _ => NotFound(JsonUtils.toJson(ErrorResponseWithPath(path, "Not found")))
    }
  }

  def addLink(path: String) = AuthAction().async { request =>
    val user = request.user
    val timeNow = DateTime.now().getMillis
    (configuration ? AddLink(Path.from(path), Path.from(JsonUtils.fromJson(request.body.asText.get).toString), user, timeNow)).map {
      case ex: Throwable => getExceptionResult(ex)
      case result: Unit => Ok(JsonUtils.toJson(StatusResponse(path, "Added")))
    }
  }

  def add(path: String) = AuthAction().async { request =>
    val user = request.user
    val timeNow = DateTime.now().getMillis
    (configuration ? Add(Path.from(path), Tree(JsonUtils.fromJson(request.body.asText.get))(NodeMeta(user, timeNow)), user, timeNow)).map {
      case ex: Throwable => getExceptionResult(ex)
      case result: Unit => Ok(JsonUtils.toJson(StatusResponse(path, "Added")))
    }
  }

  def patch(path: String) = AuthAction().async { request =>
    val user = request.user
    val timeNow = DateTime.now().getMillis
    (configuration ? Patch(Path.from(path), Tree(JsonUtils.fromJson(request.body.asText.get))(NodeMeta(user, timeNow)), user, timeNow)).map {
      case ex: Throwable => getExceptionResult(ex)
      case result: Unit => Ok(JsonUtils.toJson(StatusResponse(path, "Added")))
    }
  }

  def delete(path: String) = AuthAction().async { request =>
    val user = request.user
    val timeNow = DateTime.now().getMillis
    (configuration ? Delete(Path.from(path), user, timeNow)).map {
      case ex: Throwable => getExceptionResult(ex)
      case result: Unit => Ok(JsonUtils.toJson(StatusResponse(path, "Deleted")))
    }
  }

  def addDefault(atPath: String, relativePath: String) = AuthAction().async { request =>
    val user = request.user
    val timeNow = DateTime.now().getMillis
    (configuration ? AddDefault(Path.from(atPath),Path.from(relativePath), Tree(JsonUtils.fromJson(request.body.asText.get))(NodeMeta(user, timeNow)), user, timeNow)).map {
      case ex: Throwable => getExceptionResult(ex)
      case result: Unit => Ok(JsonUtils.toJson(StatusResponse(atPath, s"Added value for $relativePath")))
    }
  }

  def deleteDefault(atPath: String, relativePath: String) = AuthAction().async { request =>
    val user = request.user
    val timeNow = DateTime.now().getMillis
    (configuration ? DeleteDefault(Path.from(atPath), Path.from(relativePath), user, timeNow)).map {
      case ex: Throwable => getExceptionResult(ex)
      case result: Unit => Ok(JsonUtils.toJson(StatusResponse(atPath, s"Added value for $relativePath")))
    }
  }

  def snapshot = AuthAction().async { request =>
    val user = request.user
    val timeNow = DateTime.now().getMillis
    configuration ! Snapshot
    Future.successful(Accepted("Snapshot request accepted"))
  }
  def getExceptionResult: PartialFunction[Throwable, Result] = {
    case CyclicReferenceException(path, another) => BadRequest(JsonUtils.toJson(ErrorResponse("Cyclic reference detected between " + path + " and "+ another)))
    case ex:ReferenceNotFoundException => NotFound(JsonUtils.toJson(ErrorResponse("Reference not found")))
    case PathNotFoundException(path) => NotFound(JsonUtils.toJson(ErrorResponseWithPath(path.toString, "Path not found")))
    case IllegalOperationException(path, message) => BadRequest(JsonUtils.toJson(ErrorResponseWithPath(path.toString, message)))
    case PathParseFailedException(path, reason) => BadRequest(JsonUtils.toJson(ErrorResponseWithPath(path.toString, s"Path parsing failed due to : $reason")))
    case InvalidActorOperationException(reason) => BadRequest(JsonUtils.toJson(ErrorResponse(reason)))
    case SnapshotSaveException(reason) => InternalServerError(JsonUtils.toJson(ErrorResponse("Snapshot save failed with " + reason)))
    case e => Logger.error("Unhandled exception",e);InternalServerError(JsonUtils.toJson(ErrorResponse("Something went wrong")))
  }
}