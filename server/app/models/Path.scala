package models

import scala.util.parsing.combinator.RegexParsers
trait Filter[+T] {
  def matches(node:Node)(implicit references: References):Boolean
}

case class Equals[T](path:Path, value:T) extends Filter[T] {
  override def toString =  value match {
    case _:String => path.toString + " = \"" + value + "\""
    case _ => path.toString + " = " + value
  }

  override def matches(node:Node)(implicit references: References) = node.traverse(path).exists(_.asValue == value)
}

case class Contains[T](path:Path, value:T) extends Filter[T] {
  override def toString =  value match {
    case _:String => path.toString + " contains \"" + value + "\""
    case _ => path.toString + " contains " + value
  }

  override def matches(node:Node)(implicit references: References) = node.traverse(path).exists(_.asValue.asInstanceOf[List[T]].contains(value))
}

case class Refers(path:Path, value:Long) extends Filter[String] {
  override def toString =  value match {
    case _:Long => path.toString + " references to \"" + value + "\""
  }

  override def matches(node:Node)(implicit references: References) = {
    path match {
      case Path(Nil)  => node.isReference && node.asReferences.asInstanceOf[Long] == value
      case path => node.traverse(path).exists(t => t.isReference && t.asReferences.asInstanceOf[Long] == value)
    }
  }
}

case class Exists[T](path: Path) extends Filter[T] {
  override def toString = path.toString + " ?"
  override def matches(node: Node)(implicit references: References) = node.exists(path)
}

case class And[T](first: Filter[T], second: Filter[T]) extends Filter[T] {
  override def toString = "( " + first.toString + " && " + second.toString + " )"
  override def matches(node: Node)(implicit references: References) = first.matches(node) && second.matches(node)
}

case class Or[T](first: Filter[T], second: Filter[T]) extends Filter[T] {
  override def toString = "( " + first.toString + " || " + second.toString + " )"
  override def matches(node: Node)(implicit references: References) = first.matches(node) || second.matches(node)
}

case class Not[T](first: Filter[T]) extends Filter[T] {
  override def toString = "! ( " + first.toString + " )"
  override def matches(node: Node)(implicit references: References) = !first.matches(node)
}

sealed trait PathComponentMatcher
case class MatchNamespace(name: String) extends PathComponentMatcher {
  override def toString = name.replaceAll("[.]","\\\\.")
}
case class Match[T](filter:Filter[T]) extends PathComponentMatcher {
  override def toString =  "[ " + filter.toString + " ]"
}

case class MatchValue[T](path: Path, value: T) extends PathComponentMatcher {
  override def toString = value match {
    case _: String => "[ " + path.toString + " = \"" + value + "\" ]"
    case _ => "[ " + path.toString + " = " + value + " ]"
  }
}

case class MatchIndex(index: Int) extends PathComponentMatcher {
  override def toString = s"[$index]"
}

case class SelectKeys(keys:Set[String]) extends PathComponentMatcher {
  override def toString = "{" + keys.mkString(", ") + "}"
}

case object WildCardMatch extends PathComponentMatcher {
  override def toString = "*"
}

@SerialVersionUID(1L)
case class Path(path:List[PathComponentMatcher]) {
  def append(key:PathComponentMatcher) = Path(path :+ key)
  def affects(other: Path) = path.take(other.path.size) == other.path || other.path.take(path.size) == path
  override def toString = Path.asString(path)
  def next = path.head
  def rest = Path(path.tail)
}

object Path {
  object PathParser extends RegexParsers {
    val DOT = "\\."
    val SLASH = "\\\\"
    val EQUALS = "\\="
    val BRACK_OPEN = "\\["
    val BRACK_CLOSE = "\\]"
    val PAREN_OPEN  = "\\("
    val PAREN_CLOSE = "\\)"
    val COMMA = "\\,"
    val CURLY_OPEN = "\\{"
    val CURLY_CLOSE = "\\}"
    val STAR = "\\*"
    val NOT = "\\!"
    val AND = "\\&"
    val OR = "\\|"
    val QUESTION = "\\?"
    val TXT = "[^*.!&?|\\=\\[\\]\\\\\\(\\)\\@\\#,{}]".r

    def path:Parser[Path] = repsep(namespace|matches|wildcard|listIndexMatch|selectKeys,".")^^{case components => Path(components)}
    def contains = (path~"@"~value)^^{case p~_~v => Contains(p, v) }
    def refers = (path~"#"~value)^^{case p~_~v => v match {
      case key: Int => Refers(p, key)
    } }
    def equals = (path~"="~value)^^{case p~_~v => Equals(p, v) }
    def exists = (path<~"?")^^{case p => Exists(p)}
    def predicate = disjunction
    def disjunction: PathParser.Parser[Filter[Any]] = (conjunction~opt("|"~predicate))^^{
        case f~Some(_~s) => Or(f, s)
        case f~None => f
    }
    def conjunction: PathParser.Parser[Filter[Any]] = (negation~opt("&"~predicate))^^{
      case f~Some(_~s) => And(f, s)
      case f~None => f
    }
    def negation: PathParser.Parser[Filter[Any]] = ("!"~paren)^^{case "!"~pred => Not(pred)} | paren
    def paren: PathParser.Parser[Filter[Any]] = "("~predicate~")"^^{case "("~pred~")" => pred} | filters
    def filters = equals | contains | exists | refers
    def matches:Parser[PathComponentMatcher] = "["~predicate~"]"^^{case "["~pred~"]" => Match(pred)}
    def key: PathParser.Parser[String] = ((TXT|DOT|SLASH|BRACK_OPEN|BRACK_CLOSE|STAR|PAREN_OPEN|PAREN_CLOSE|COMMA|CURLY_OPEN|CURLY_CLOSE|AND|OR|NOT|QUESTION)+)^^{case key => key.map(_.replaceAll("\\\\.",".")).mkString("")}
    def namespace: PathParser.Parser[MatchNamespace] = key^^{case name: String => MatchNamespace(name)}
    def selectKeys = "{"~repsep(key, ",")~"}"^^{case "{"~keys~"}" => SelectKeys(keys.toSet)}
    def wildcard = "*"^^{case "*" => WildCardMatch}
    def stringValue = "\""~"[^\"]*".r~"\"" ^^{case _~value~_ => value}
    def integerValue = "[0-9]+".r ^^{case num => num.toInt}
    def doubleValue = "[0-9]*\\.[0-9]+".r ^^{case num => num.toDouble}
    def booleanValue= ("true"|"false")^^{case bool => bool.toBoolean}
    def value = stringValue | doubleValue | integerValue | booleanValue
    def listIndexMatch = "["~"[0-9]+".r~"]"^^{case "["~index~"]" => MatchIndex(index.toInt)}


    def parse(value:String) = parseAll(path,value) match {
      case Success(path, _) => path
      case Failure(msg,_) => {
        println(msg)
        throw PathParseFailedException(value,msg)
      }
      case Error(msg,_) => throw PathParseFailedException(value,msg)
    }
  }

  def from(pathStr:String) = PathParser.parse(pathStr)
  def asString(path:List[PathComponentMatcher]) = path.map(_.toString).mkString(".")
}

