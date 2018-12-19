package com.indix.wasp.authentication

import com.typesafe.config.Config


trait AuthenticatorProvider {
  def create(config: Config): Authenticator
}