package domain

import akka.persistence.PersistentView
import models._
import akka.actor._
import akka.pattern.AskSupport
import models.InvalidActorOperationException
import scala.util.Try

/*
 * Used to display the last 50 commands that were persisted in the journal.
 */
class HistoryView(path:Option[Path]) extends PersistentView {

  val listSize = 300
  var payloadList = List[WaspEvent]()
  val waspVersion = Global.waspVersion

  override def viewId: String = Global.historyViewId

  override def persistenceId: String = Global.journalPersistenceId

  override def receive: Receive = {
    case waspEvent: WaspEvent if !path.isDefined || path.exists(waspEvent.command.isAffected) =>
      payloadList = waspEvent :: payloadList.take(listSize - 1)
    case AllChanges => sender ! payloadList.map {case WaspEvent(seqNo, command, waspVersion) =>
      Map("seqNo" -> seqNo, command.getClass.getSimpleName -> command)
    }.toList
    case payload => sender ! ErrorHandler.handleErrors({ throw InvalidActorOperationException(s"Received Payload: ${payload}, which is not a Command") })
  }
}

/*
 * Once a particular seqNo of a Command is passed, it creates a view with the state of the configuration after that command is performed.
 */
class RestoreView(restoreSeqNo: Long) extends PersistentView with State {

  var foundRestorePoint = false

  val waspVersion = Global.waspVersion

  override def viewId: String = Global.restoreViewId

  override def persistenceId: String = Global.journalPersistenceId

  override def receive: Actor.Receive = {
    case Restore(path) => sender ! root.compute(path).map(_.asValue)
    case WaspEvent(seqNo, command, waspVersion) => if(!foundRestorePoint) Try{ perform(command) }
                                            if(seqNo == restoreSeqNo) foundRestorePoint = true

    case payload => sender ! ErrorHandler.handleErrors({ throw InvalidActorOperationException(s"Received Payload: ${payload}, which is not a Command") })
  }
}

class History(system:ActorSystem) extends AskSupport {

  import Global.recoveryTimeout
  import scala.concurrent.ExecutionContext.Implicits.global

  def history() = {
    val view: ActorRef = system.actorOf(Props.create(classOf[HistoryView],None))
    (view ? AllChanges) andThen {case _ => view ! PoisonPill}
  }

  def history(path:Path) = {
    val view: ActorRef = system.actorOf(Props.create(classOf[HistoryView], Some(path)))
    (view ? AllChanges) andThen {case _ => view ! PoisonPill}
  }

  def restore(seqNo: Long, path: Path) = {
    val view = system.actorOf(Props(classOf[RestoreView], seqNo))
    (view ? Restore(path)) andThen {case _ => view ! PoisonPill}
  }
}
