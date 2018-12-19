package migration

import akka.actor.{ActorSystem, Props}
import akka.pattern.AskSupport
import akka.persistence.{PersistentActor, Recover, RecoveryCompleted}
import com.indix.wasp.models.User
import com.typesafe.config.ConfigValueFactory
import domain.Global
import domain.Global._
import models._
import org.joda.time.DateTime

class WaspEventMigrator extends PersistentActor with AskSupport {
  val newSettings = settings.withValue("akka.persistence.journal.leveldb.dir", ConfigValueFactory.fromAnyRef("/home/addnab/sandbox/wasp-journal-new"))
  val newActorSystem = ActorSystem("wasp", newSettings)
  val newWaspEventWriter = newActorSystem.actorOf(Props[NewWaspEventWriter])

  val user = User("Admin", "admin@indix.com")
  val timestamp = DateTime.now().getMillis
  val nodeMeta = NodeMeta(user, timestamp)
  val waspVersion = Global.waspVersion

  override def preStart = {
    self ! Recover()
  }

  override def receiveCommand = {
    case "Migrate" => println("Migrating")
  }

  def updatePath(path:Path) = {
    Path(path.path.map {
        case MatchValue(p, value) => Match(Equals(p, value))
        case p => p
    })
  }

  def migrateCommand: PartialFunction[Command, Command] = {
    case add: Add => add.copy(user=user, timestamp=timestamp, value = Tree.updateNodeMeta(add.value)(nodeMeta), path = updatePath(add.path))
    case delete: Delete => delete.copy(user=user, timestamp=timestamp, path = updatePath(delete.path))
    case addDefault: AddDefault => addDefault.copy(user=user, timestamp=timestamp, value = Tree.updateNodeMeta(addDefault.value)(nodeMeta))
    case deleteDefault: DeleteDefault => deleteDefault.copy(user=user, timestamp=timestamp)
  }

  override def receiveRecover = {
    case RecoveryCompleted => {
      println("Recovery completed")
    }

    case (seqNo: Long, command: Command) => {
      newWaspEventWriter ! WaspEvent(seqNo, migrateCommand(command), waspVersion)
    }
  }

  override def persistenceId: String = Global.journalPersistenceId
}

class NewWaspEventWriter extends PersistentActor with AskSupport {

  override def receiveCommand = {
    case waspEvent: WaspEvent => persist(waspEvent)(waspEvent => println(s"Done Persisting ${waspEvent.seqNo}") )
  }

  override def receiveRecover = {
    case _ => println("Recovery")
  }

  override def persistenceId: String = Global.journalPersistenceId
}

