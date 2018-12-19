package migration

import akka.actor.{ActorSystem, Props}
import akka.pattern.AskSupport
import com.typesafe.config.ConfigValueFactory
import domain.Global.settings

object MigrateEventOneTime extends App with AskSupport{
  val oldSettings = settings.withValue("akka.persistence.journal.leveldb.dir", ConfigValueFactory.fromAnyRef("/home/addnab/sandbox/wasp-journal-old"))
  val oldActorSystem = ActorSystem("wasp", oldSettings)
  val waspEventMigrator = oldActorSystem.actorOf(Props[WaspEventMigrator])


  (waspEventMigrator ! "Migrate")
}
