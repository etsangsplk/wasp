package controllers

import akka.pattern.AskSupport
import play.api.Play
import play.api.Play.current
import play.api.mvc.{Action, Controller}
import plugins.HealthCheckPlugin
import utils.JsonUtils
import scala.concurrent.ExecutionContext.Implicits.global

case class HealthCheckResponse(status: String, uptime: Long)

object HealthCheckController extends Controller with AskSupport {

  def healthCheck = Play.application.plugin[HealthCheckPlugin]
    .getOrElse(throw new RuntimeException("HealthCheckPlugin not loaded"))

  def get() = Action.async {
      healthCheck.isHealthy.map{
       case "OK" =>  Ok(JsonUtils.toJson(HealthCheckResponse("OK", healthCheck.upTime)))
       case _ => InternalServerError(JsonUtils.toJson(HealthCheckResponse("FAILURE", healthCheck.upTime)))
     }
  }
}
