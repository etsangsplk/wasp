package com.indix.wasp.authentication.simple

import com.indix.wasp.authentication.{AuthenticationErrorException, Authenticator}
import com.indix.wasp.models.User
import com.indix.wasp.authentication.HttpRequest
import com.typesafe.config.Config

import scala.concurrent.{ExecutionContext, Future}

case class FileBasedAuthenticatorPlugin(configuration: Config, validUsers : List[ValidUser]) extends Authenticator {

  override def authenticate[A](request: HttpRequest[A])(implicit executionContext: ExecutionContext): Future[User] = {
    val email = request.queryParam("email").getOrElse("")
    val password = request.queryParam("password").getOrElse("")
    val isAdmin = request.queryParam("isAdmin").getOrElse("false").toBoolean
    val isCredentialValid = validUsers.exists(validUser => {
      validUser.email == email && validUser.password == password
    })
    if (isCredentialValid) {
      Future(User(email,password,isAdmin))
    } else {
      throw new AuthenticationErrorException()
    }
  }

  override def defaultUser(implicit executionContext: ExecutionContext): User = {
    val userName = configuration.getString("default.user.name")
    val userEmail = configuration.getString("default.user.email")
    val userIsAdmin = configuration.getBoolean("default.user.isAdmin")
    User(userName,userEmail,userIsAdmin)
  }
}
