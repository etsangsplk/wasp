package models

import org.joda.time.format.DateTimeFormat
import utils.JsonUtils
import com.indix.wasp.models.User

sealed trait Event

sealed trait Command extends Event {
  //Command will never have a node which contains a reference
  @transient def asValue(implicit references: References = new References()): Any
  @transient def isAffected(path:Path):Boolean
}
sealed trait Query extends Event

@SerialVersionUID(10000L)
case class Add(path: Path, value: Node, user: User, timestamp: Long) extends Command {
  override def toString = "\"Add\": { \"path\": \"" + path + "\", \"value\": " + value + ", \"user\": \"" + user + "\"" + ", \"timestamp\": " + timestamp + "}"
  override def asValue(implicit references: References) = Map("path" -> path.toString, "value" -> value.asValue, "user" -> user.toString, "timestamp" -> DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss").print(timestamp))
  override def isAffected(path:Path) = this.path.affects(path)
}
@SerialVersionUID(20000L)
case class Delete(path:Path, user:User, timestamp: Long) extends Command {
  override def toString = "\"Delete\": { \"path\": \"" + path.toString + "\", \"user\": \"" + user + "\"" + ", \"timestamp\": " + timestamp + "}"
  override def asValue(implicit references: References) = Map("path" -> path.toString, "user" -> user.toString, "timestamp" -> DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss").print(timestamp))
  override def isAffected(path:Path) = this.path.affects(path)
}
@SerialVersionUID(30000L)
case class AddDefault(atPath: Path, relativePath: Path, value: Node, user:User, timestamp: Long) extends Command {
  override def toString = "\"AddDefault\": { \"atPath\": \"" + atPath + "\", \"relativePath\": \"" + relativePath + "\", \"value\": " + JsonUtils.toJson(value) + ", \"user\": \"" + user + "\"" + ", \"timestamp\": " + timestamp + "}"
  override def asValue(implicit references: References) = Map("atPath" -> atPath.toString, "relativePath" -> relativePath.toString, "value" -> value.asValue, "user" -> user.toString, "timestamp" -> DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss").print(timestamp))
  override def isAffected(path:Path) = this.atPath.affects(path)
}
@SerialVersionUID(40000L)
case class DeleteDefault(atPath: Path, relativePath: Path, user:User, timestamp: Long) extends Command {
  override def toString = "\"AddDefault\": { \"atPath\": \"" + atPath + "\", \"relativePath\": \"" + relativePath + "\", \"user\": \"" + user + "\"" + ", \"timestamp\": " + timestamp + "}"
  override def asValue(implicit references: References) = Map("atPath" -> atPath.toString, "relativePath" -> relativePath.toString, "user" -> user.toString, "timestamp" -> DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss").print(timestamp))
  override def isAffected(path:Path) = this.atPath.affects(path)
}
@SerialVersionUID(50000L)
case class AddLink(source: Path, target: Path, user: User, timestamp: Long) extends Command {
  override def toString = "\"AddLink\": { \"source\": \"" + source + "\", \"target\": " + target + ", \"user\": \"" + user + "\"" + ", \"timestamp\": " + timestamp + "}"
  override def asValue(implicit references: References) = Map("source" -> source, "target" -> target, "user" -> user.toString, "timestamp" -> DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss").print(timestamp))
  override def isAffected(path:Path) = this.source.affects(path) || this.target.affects(path)
}
@SerialVersionUID(60000L)
case class Patch(path: Path, value: Node, user: User, timestamp: Long) extends Command {
  override def toString = "\"Patch\": { \"path\": \"" + path + "\", \"value\": " + value + ", \"user\": \"" + user + "\"" + ", \"timestamp\": " + timestamp + "}"
  override def asValue(implicit references: References) = Map("path" -> path.toString, "value" -> value.asValue, "user" -> user.toString, "timestamp" -> DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss").print(timestamp))
  override def isAffected(path:Path) = this.path.affects(path)
}

case class FindConfig(path:Path, withDefaults: Boolean) extends Query
case class FindReferences(path:Path) extends Query
case class FindKeys(path: Path) extends Query
case class FindDefaults(path: Path) extends Query
case class Restore(path: Path) extends Query
case object AllChanges extends Query

case object Snapshot extends Event