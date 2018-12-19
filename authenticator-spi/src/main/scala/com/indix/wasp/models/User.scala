package com.indix.wasp.models

case class User(name: String, email: String, isAdmin: Boolean = false) {
  override def toString = s"$name - $email"
}
