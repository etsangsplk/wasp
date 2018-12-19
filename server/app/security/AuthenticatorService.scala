package security

import java.util
import java.util.ServiceLoader

import com.indix.wasp.authentication.{Authenticator, AuthenticatorProvider, HttpRequest}
import com.indix.wasp.models.User
import models.{InvalidUserException, PluginNotFoundException}
import play.api.Configuration

import scala.concurrent.{ExecutionContext, Future}

import scala.collection.JavaConverters.asScalaIteratorConverter

class AuthenticatorService(authenticator: Authenticator) {

  def authenticate[A](request: HttpRequest[A])(implicit executionContext: ExecutionContext): Future[User] = {
    val user = authenticator.authenticate(request)
    if (user == null) {
      throw InvalidUserException("Plugin returned a null user response for the request")
    } else {
      user
    }
  }

  def defaultUser(implicit executionContext: ExecutionContext): User = {
    authenticator.defaultUser(executionContext)
  }
}

object AuthenticatorService {
  def instance(config: Configuration): AuthenticatorService = {
    val loader = ServiceLoader.load(classOf[AuthenticatorProvider])
    val authenticatorProviders = loader.iterator().asScala.toList
    if (authenticatorProviders.isEmpty) {
      throw PluginNotFoundException("The provider implementation not found in plugin")
    }
    val authenticatorProvider = authenticatorProviders.head
    val authenticator = authenticatorProvider.create(config.underlying)
    new AuthenticatorService(authenticator)
  }
}
