package security

import com.indix.wasp.authentication.AuthenticationErrorException
import com.indix.wasp.models.User
import controllers.{ErrorResponse, ErrorResponseWithPath}
import play.api.Configuration
import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc.Results._
import play.api.mvc.Security.AuthenticatedRequest
import play.api.mvc.{ActionBuilder, Request, Result}
import utils.{JsonUtils, RequestFacade}

import scala.concurrent.Future

case class AuthAction(implicit config: Configuration, authenticationService: AuthenticatorService) extends ActionBuilder[({type R[A] = AuthenticatedRequest[A, User]})#R] {

  def invokeBlock[A](request: Request[A], block: ((AuthenticatedRequest[A, User]) => Future[Result])): Future[Result] = {

    def checkPermissionsAndExecuteAction(user: User, path: String): Future[Result] = WaspPermissions.isUserPermitted(config, user, path, request.method) match {
      case true => block(new AuthenticatedRequest(user, request))
      case _ => Future.successful(Forbidden(JsonUtils.toJson(ErrorResponseWithPath(path, s"Not permitted to make a ${request.method} request on this path"))))
    }

    //waspAuth feature flag
    val waspAuth = config.getBoolean("wasp.auth")

    if (!waspAuth.getOrElse(false))
      return block(new AuthenticatedRequest(authenticationService.defaultUser(executionContext), request))

    val requestFacad = new RequestFacade[A](request)

    val userResponseFuture: Future[User] = authenticationService.authenticate(requestFacad).recover {
      case _ => throw new AuthenticationErrorException()
    }

    val path = request.getQueryString("path").getOrElse("")

    val resultResponseFuture = for {
      user: User <- userResponseFuture
      result <- checkPermissionsAndExecuteAction(user, path)
    } yield result

    resultResponseFuture.recover {
      case ex: AuthenticationErrorException => Forbidden(JsonUtils.toJson(ErrorResponse("Authorization Failed")))
      case ex => BadRequest(JsonUtils.toJson(ErrorResponse(ex.getMessage)))
    }
  }
}