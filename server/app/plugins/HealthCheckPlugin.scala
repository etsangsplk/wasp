package plugins

import akka.pattern.AskSupport
import domain.{Global, Ping}
import play.api.libs.json.{JsValue, Json}
import play.api.{Application, Plugin}

import scala.concurrent.ExecutionContext.Implicits.global

class HealthCheckPlugin(app: Application) extends Plugin with AskSupport {
  import Global.{askTimeout, configuration}

  override def enabled = true
  private var startTime = 0: Long

  override def onStart() = {
    startTime = System.currentTimeMillis()
  }

  def upTime = (System.currentTimeMillis() - startTime)/1000
  def isHealthy = (configuration ? Ping).map(_ => "OK").recover{case _ => "FAILED"}

}