package utils

import com.indix.wasp.authentication.HttpRequest
import models.Path
import play.api.mvc.Request

class RequestFacade[A](request: Request[A], path: String) extends HttpRequest[A] {
  def this(request: Request[A]) = this(request, request.uri)

  val headers: Map[String, Seq[String]] = request.headers.toMap
  val params = request.queryString.map { case (k,v) => k -> v.mkString }
  val remoteAddr = request.remoteAddress
  override def header(name: String): Seq[String] = headers.getOrElse(name.toLowerCase, Seq.empty)
  override def queryParam(name: String): Option[String] = params.get(name)
  val method: String = request.method
}
