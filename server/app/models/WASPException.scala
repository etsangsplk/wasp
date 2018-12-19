package models

sealed trait WASPException extends RuntimeException

case class PathNotFoundException(path:Path) extends WASPException
class ReferenceNotFoundException extends WASPException
case class CyclicReferenceException(path:Path, another: Path) extends WASPException
case class IllegalOperationException(path:Path,message:String) extends WASPException
case class PathParseFailedException(path:String,reason:String) extends WASPException
case class InvalidActorOperationException(reason: String) extends WASPException
case class SnapshotSaveException(message: String) extends WASPException
case class InvalidUserException(message: String) extends WASPException
case class PluginNotFoundException(message: String) extends WASPException