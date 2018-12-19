package models

import org.joda.time.DateTime
import com.indix.wasp.models.User

case class NodeMeta(createdBy: User, lastModifiedBy: User, createdTime: Long, modifiedTime: Long) {
  def modify(user: User, timestamp: Long) = this.copy(lastModifiedBy=user, modifiedTime=timestamp)
  def getAuthor = createdBy.name
  def modifiedAt = new DateTime(modifiedTime).toString()
}

object NodeMeta {
  def apply(user: User, timestamp: Long): NodeMeta = apply(user, user, timestamp, timestamp)
}