package wasp

import java.io.ByteArrayOutputStream

import io.netty.handler.codec.http.HttpHeaders
import org.asynchttpclient._
import org.asynchttpclient.AsyncHandler.State

import scala.concurrent.Promise

case class HttpResponse(body:String, status:Int)

class HttpClient {
  val client: AsyncHttpClient = new DefaultAsyncHttpClient()
  def get(url:String, params:Map[String,String] = Map.empty) = {
    val handler = new HttpHandler
    val method = client.prepareGet(url)
    params.foreach(t => method.addQueryParam(t._1,t._2))
    method.execute(handler)
    handler.future
  }

  def post(url:String, params:Map[String,String] = Map.empty, body:Option[String]) = {
    val handler = new HttpHandler
    val method = client.preparePost(url)
    params.foreach(t => method.addQueryParam(t._1,t._2))
    body.foreach(method.setBody)
    method.setHeader("Content-Type", "text/plain")
    method.execute(handler)
    handler.future
  }

  def delete(url:String, params:Map[String,String] = Map.empty) = {
    val handler = new HttpHandler
    val method = client.prepareDelete(url)
    params.foreach(t => method.addQueryParam(t._1,t._2))
    method.execute(handler)
    handler.future
  }
  def shutdown() = client.close()
}

class HttpHandler extends AsyncHandler[Unit] {
  private val bytes = new ByteArrayOutputStream()
  private var status = 200
  private val promise = Promise[HttpResponse] ()

  override def onHeadersReceived(headers: HttpHeaders): State = State.CONTINUE
  override def onStatusReceived(httpResponseStatus: HttpResponseStatus): State = {
    status = httpResponseStatus.getStatusCode
    State.CONTINUE
  }

  override def onBodyPartReceived(httpResponseBodyPart: HttpResponseBodyPart): State = {
    bytes.write(httpResponseBodyPart.getBodyPartBytes)

    State.CONTINUE
  }

  override def onThrowable(throwable: Throwable): Unit = promise.failure(throwable)
  override def onCompleted(): Unit = promise.success(HttpResponse(bytes.toString("UTF-8"), status))
  def future = promise.future
}
