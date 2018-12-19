package domain

import akka.actor.{Props, ActorSystem}
import akka.util.Timeout
import com.typesafe.config.ConfigFactory

import scala.concurrent.duration.DurationDouble

object Global {

  implicit val askTimeout: Timeout = 50 seconds

  implicit val recoveryTimeout: Timeout = 1 hour

  val settings = ConfigFactory.load
  
  val journalPersistenceId = settings.getString("wasp.persistence-id")

  val historyViewId = "History-View"

  val restoreViewId = "Recover-View"

  val waspVersion = settings.getString("wasp.version")

  val actorSystem = ActorSystem("wasp", settings)

  val configuration = actorSystem.actorOf(Props[Configuration])
}

