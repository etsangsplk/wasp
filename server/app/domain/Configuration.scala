package domain

import akka.pattern.AskSupport
import akka.persistence.{PersistentActor, Recover, RecoveryCompleted, SaveSnapshotFailure, SaveSnapshotSuccess, SnapshotOffer}
import models.{Add, AddDefault, Delete, DeleteDefault, FindConfig, Namespace, _}
import org.joda.time.DateTime
import play.api.Logger
import com.indix.wasp.models.User


import scala.util.Try

case object Ping
case object Pong
case object GetChanges

trait State {
  implicit val nodemeta = NodeMeta(User("Admin", "admin@indix.com"), 0)
  var root: Node = Namespace()
  implicit var references = new References()

  def perform:PartialFunction[Command,Unit] = {
    case Add(path, value, user, timestamp)                => root = root.add(value, user, timestamp)(path)
    case Patch(path, value, user, timestamp)              => root = root.patch(value, user, timestamp)(path)
    case AddLink(path, sourcePath, user, timestamp)       =>
      val index = root.extractRef(sourcePath)
      val node = root.traverse(path)
      root = root.add(new Reference(index), user, timestamp)(sourcePath)
      root = root.add(new Reference(index), user, timestamp)(path)
      if(root.cycles()){
        if(node.isDefined)
          root = root.add(node.get, user, timestamp)(path)
        else
          root = root.delete(user, timestamp)(path)
        throw CyclicReferenceException(path, sourcePath)
      }
    case Delete(path, user, timestamp)                    => root = root.delete(user, timestamp)(path)
    case AddDefault(atPath, path, value, user, timestamp) => root = root.addDefault(atPath, user, timestamp)(path, value)
    case DeleteDefault(atPath, path, user, timestamp)     => root = root.deleteDefault(atPath, user, timestamp)(path)
  }

  def query:PartialFunction[Query, Option[(Any, NodeMeta)]] = {
    case FindConfig(path, true) => root.compute(path).map(value => (value.asValue, value.getNodeMeta))
    case FindConfig(path, false) => root.traverse(path).map(value => (value.asValue, value.getNodeMeta))
    case FindReferences(path) => root.traverse(path).map(value => (value.asReferences, value.getNodeMeta))
    case FindKeys(path)   => root.traverse(path).map(value => (value.keys.getOrElse(List.empty), value.getNodeMeta))
    case FindDefaults(path) => root.traverse(path) match {
      case Some(node) => node.getDefaults match {
        case Some(defaults) => Some(defaults.value, node.getNodeMeta)
        case None => None
      }
      case None => None
    }
  }
}

class Configuration extends PersistentActor with AskSupport with State {
  var seqNo = DateTime.now.getMillis
  override def preStart = {
    self ! Recover()
  }

  val waspVersion = Global.waspVersion

  override def receiveCommand = {
    case command: Command => this.synchronized {
      persist(WaspEvent(seqNo, command, waspVersion))(waspEvent => sender ! ErrorHandler.handleErrors(perform(waspEvent.command)))
      seqNo = seqNo + 1
    }
    case query: Query => sender ! (try{this query query} catch {case ex:PathNotFoundException => None
      case ex:ReferenceNotFoundException => None
    })

    case Snapshot => saveSnapshot((root, references))
    case success: SaveSnapshotSuccess =>
      Logger.info(s"SaveSnapshotSuccess: metadata=${success.metadata}")
    case failure: SaveSnapshotFailure =>
      Logger.error(s"SaveSnapshotFailure: metadata=${failure.metadata}, reason=${failure.cause}")
    case Ping => sender ! Pong
  }

  override def receiveRecover = {
    case RecoveryCompleted    => Logger.info("Recovery Finished")
    case SnapshotOffer(metadata, snapshot: (Node, References)) =>
      Logger.info(s"Recovery from snapshot ${metadata} triggered!!")
      root= snapshot._1
      references = snapshot._2
    case waspEvent: WaspEvent =>
      Try(perform(waspEvent.command)).recover({
        case e:WASPException =>
        case e:IllegalOperationException =>
        case e:Throwable => {
          e.printStackTrace()
          throw e
        }
      })
  }

  override def persistenceId: String = Global.journalPersistenceId
}

object ErrorHandler {
  def handleErrors(block: => Any) = {
    Try(block).recoverWith(catchKnown).get
  }

  def catchKnown:PartialFunction[Throwable, Try[Exception]] = {
    case waspEx: WASPException => Try{waspEx}
    case e => Logger.error("Unhandled exception",e)
              Try{new RuntimeException("Something went wrong")}
  }
}
