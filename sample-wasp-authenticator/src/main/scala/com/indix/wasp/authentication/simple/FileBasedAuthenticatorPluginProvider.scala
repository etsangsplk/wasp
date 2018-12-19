package com.indix.wasp.authentication.simple

import java.io.File

import com.indix.wasp.authentication.{Authenticator, AuthenticatorProvider}
import com.typesafe.config.Config

import scala.io.Source

class FileBasedAuthenticatorPluginProvider extends AuthenticatorProvider {

  override def create(config: Config): Authenticator = {
    val filePath: String = config.getString("auth.file-path")
    val file = new File(filePath)
    val validUsers = Source.fromFile(file.getAbsolutePath).getLines.toList.map(line =>{
      val credential = line.split(":")
      ValidUser(credential(0),credential(1),credential(2).toBoolean)
    })
    FileBasedAuthenticatorPlugin(config,validUsers)
  }
}

case class ValidUser(email: String, password: String, isAdmin: Boolean)

