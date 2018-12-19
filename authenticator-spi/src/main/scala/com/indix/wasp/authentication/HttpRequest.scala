package com.indix.wasp.authentication

/**
  * Simple abstraction for a HTTP Request.
  */
trait HttpRequest[A] {

  /**
    * Returns the name of the HTTP method with which this request was made.
    * For example, GET, POST, or PUT.
    */
  def method: String

  /**
    * Get the header values for given header name
    * @param name the name of the header
    * @return the seq of values
    */
  def header(name: String): Seq[String]


  /**
    * Get the value of a given request parameter.
    * @param name the name of the query parameter.
    * @return the value of the query parameter.
    */
  def queryParam(name: String): Option[String]

}


