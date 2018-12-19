package com.indix.wasp.authentication

import com.indix.wasp.models.User

import scala.concurrent.{ExecutionContext, Future}

trait Authenticator {
  def authenticate[A](request: HttpRequest[A])(implicit executionContext: ExecutionContext): Future[User]

  def defaultUser(implicit executionContext: ExecutionContext): User
}

