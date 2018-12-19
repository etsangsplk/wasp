package security

object Access extends Enumeration {
  type Access = Value
  val READ = Value("R")
  val WRITE = Value("W")
}
