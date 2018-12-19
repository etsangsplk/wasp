package models

import com.indix.wasp.models.User

@SerialVersionUID(10L)
class References(var namespace:Map[Long, Node] = Map.empty, var lastIndex:Long = 0L) extends Serializable {

  def next(key: Long) = namespace(key).allReferences
  def get(key: Long) = namespace(key)

  private def updateNamespace(key: Long, value: Node) = {
    namespace = namespace.updated(key, value)
    lastIndex = lastIndex+1
  }

  def addRef(value: Node) = {
    val key = lastIndex
    updateNamespace(key, value)
    key
  }

  def deleteRef(key: Long) = namespace = namespace.-(key) //Reference count could jump because of cycle detection

  def traverse(key: Long):PartialFunction[Path,Option[Node]] = {
    case Path(path) => namespace(key).traverse(Path(path))(this)
  }

  def add(key: Long, value: Node, user: User, timestamp: Long):PartialFunction[Path, Unit] = {
    case Path(path) => namespace = namespace.updated(key, namespace(key).add(value, user, timestamp)(Path(path))(this))
  }

  def patch(key: Long, value: Node, user: User, timestamp: Long):PartialFunction[Path, Unit] = {
    case Path(path) =>  val toPatch = key -> namespace(key).patch(value, user, timestamp)(Path(path))(this)
      namespace = namespace + toPatch
  }

  def delete(key: Long, user: User, timestamp: Long):PartialFunction[Path, Unit] = {
    case Path(path) => namespace = namespace.updated(key, namespace(key).delete(user, timestamp)(Path(path))(this))
  }

  def keys(key: Long):Option[List[String]] = namespace(key).keys(this)

  def getDefaults(key: Long): Option[Defaults] = namespace(key).getDefaults(this)

  def addDefault(key: Long, at:Path, user: User, timestamp: Long)(path:Path,value:Node):Unit = {
    namespace = namespace.updated(key, namespace(key).addDefault(at, user, timestamp)(path, value)(this))
  }

  def withDefaults(key: Long, path:Path):Node = namespace(key).withDefaults(path)(this)

  def deleteDefault(key: Long, at: Path, user: User, timestamp: Long)(path: Path): Unit = namespace = namespace.updated(key, namespace(key).deleteDefault(at, user, timestamp)(path)(this))

  def addIfNotExist(key: Long, items:Map[Path,Node]):PartialFunction[Path, Unit] = {
    case Path(path) => namespace = namespace.updated(key, namespace(key).addAllIfNotExist(items)(this))
  }

  def extractRef(key: Long):PartialFunction[Path, Long] = {
    case Path(path) => namespace(key).extractRef(Path(path))(this)
  }
  def clear: Unit = {namespace = Map.empty; lastIndex = 0L}
}

sealed trait Node {
  def addDefault(at:Path, user: User, timestamp: Long)(path:Path,value:Node)(implicit references: References):Node
  def add(value: Node, user: User, timestamp: Long)(path: Path)(implicit references: References):Node
  def patch(value: Node, user: User, timestamp: Long)(path: Path)(implicit references: References):Node
  def addIfNotExist(value:Node)(path: Path)(implicit references: References):Node
  def delete(user: User, timestamp: Long )(path: Path)(implicit references: References):Node
  def deleteDefault(at: Path, user: User, timestamp: Long)(path: Path)(implicit references: References):Node
  def keys(implicit references: References):Option[List[String]]
  def traverse(path: Path)(implicit references: References):Option[Node]
  def asValue(implicit references: References):Any
  def asReferences(implicit references: References):Any
  def withDefaults(path: Path)(implicit references: References):Node
  def getDefaults(implicit references: References): Option[Defaults]
  def getNodeMeta: NodeMeta
  def compute(path: Path)(implicit references: References):Option[Node]= withDefaults(path).traverse(path)
  def exists(path: Path)(implicit references: References):Boolean = traverse(path).isDefined
  def addAll(items:Map[Path,Node])(implicit references: References) = items.foldLeft(this)((sofar,t) => sofar.add(t._2, User("Admin", "admin@indix.com"), 0)(t._1))
  def addAllIfNotExist(items:Map[Path,Node])(implicit references: References) = items.foldLeft(this)((sofar,t) => sofar.addIfNotExist(t._2)(t._1))
  def cycles(visited: Set[Long] = Set.empty)(implicit references: References) : Boolean = {
    allReferences.exists(ref => visited.contains(ref) || references.get(ref).cycles(visited + ref))
  }
  def extractRef(path:Path)(implicit references: References):Long
  def isReference: Boolean = false
  def allReferences: Set[Long]
}

@SerialVersionUID(100L)
case class Defaults(defaults:Map[Path,Node] = Map.empty) {
  def + = (defaults.+ _).andThen(Defaults.apply)
  def - = (defaults.- _).andThen(Defaults.apply)
  def matchingName(name:String) = defaults.filter(pickKey andThen canMatch(name)).map(t => t._1 -> t._2)
  def hasAnyMatching(name:String) = defaults.exists(pickKey andThen canMatch(name))
  def all = defaults
  def pickKey[T,K]:PartialFunction[(T,K),T] = {
    case (key,value) => key
  }
  def canMatch(x:String)(path:Path) = path.next match {
    case MatchNamespace(name) if name == x => true
    case WildCardMatch => true
    case _ => false
  }
  def value(implicit references: References) = defaults.map{ case (path, node) => path -> node.asValue }
}

@SerialVersionUID(1000L)
case class Namespace(namespace: Map[String, Node] = Map.empty, defaults:Defaults = Defaults())(implicit nodeMeta: NodeMeta) extends Node {

  override def getNodeMeta() = this.nodeMeta

  override def add(value: Node, user: User, timestamp: Long)(path: Path)(implicit references: References) = path match {
    case Path(MatchNamespace(x) :: xs) => this.copy(namespace=namespace + (x -> namespace.getOrElse(x,Namespace()(NodeMeta(user, timestamp))).add(value, user, timestamp)(Path(xs))))(nodeMeta.modify(user, timestamp))
    case Path(Match(predicate) :: xs) => this.copy(namespace=namespace ++ namespace.filter({case(name, node) => predicate.matches(node)}).map(t => t._1 -> t._2.add(value, user, timestamp)(Path(xs))))
    //TODO: Now addIfNotExists will need to be defined in Node itself because of wildcards
    case Path(WildCardMatch :: xs) => this.copy(namespace=namespace.map(t => t._1 -> t._2.add(value, user, timestamp)(Path(xs))))(nodeMeta.modify(user, timestamp))
    case Path(SelectKeys(keys) :: xs) => this.copy(namespace=namespace ++ keys.map(key => key -> namespace.getOrElse(key,Namespace()(NodeMeta(user, timestamp))).add(value, user, timestamp)(Path(xs))).toMap)(nodeMeta.modify(user, timestamp))
    case Path(Nil) => value
    case path => throw new PathNotFoundException(path)
  }

  override def patch(value: Node, user: User, timestamp: Long)(path: Path)(implicit references: References) = path match {
    case Path(MatchNamespace(x) :: xs) => this.copy(namespace=namespace + (x -> namespace.getOrElse(x,Namespace()(NodeMeta(user, timestamp))).patch(value, user, timestamp)(Path(xs))))(nodeMeta.modify(user, timestamp))
    case Path(Match(predicate) :: xs) => this.copy(namespace=namespace ++ namespace.filter({case(name, node) => predicate.matches(node)}).map(t => t._1 -> t._2.patch(value, user, timestamp)(Path(xs))))
    //TODO: Now addIfNotExists will need to be defined in Node itself because of wildcards
    case Path(WildCardMatch :: xs) => this.copy(namespace=namespace.map(t => t._1 -> t._2.patch(value, user, timestamp)(Path(xs))))(nodeMeta.modify(user, timestamp))
    case Path(SelectKeys(keys) :: xs) => this.copy(namespace=namespace ++ keys.map(key => key -> namespace.getOrElse(key,Namespace()(NodeMeta(user, timestamp))).patch(value, user, timestamp)(Path(xs))).toMap)(nodeMeta.modify(user, timestamp))
    case Path(Nil) => value
    case path => throw new PathNotFoundException(path)
  }

  override def traverse(path: Path)(implicit references: References) = path match {
    case Path(MatchNamespace(x) :: xs) if namespace contains x => namespace(x).traverse(Path(xs))
    case Path(WildCardMatch :: xs) => Some(this.copy(namespace=namespace.flatMap(t => t._2.traverse(Path(xs)).map(t._1 ->))))
    case Path(SelectKeys(keys) :: xs) => Some(this.copy(namespace=namespace.filter(keys contains _._1).flatMap(t => t._2.traverse(Path(xs)).map(t._1 ->))))
    case Path(Match(predicate) :: xs) => Some(this.copy(namespace=namespace.filter(entry => predicate.matches(entry._2)).flatMap(t => t._2.traverse(Path(xs)).map(t._1 ->))))
    case Path(Nil) => Some(this)
    case path => None
  }

  override def asValue(implicit references: References) = namespace.map(t => t._1 -> t._2.asValue).toMap

  override def asReferences(implicit references: References) = throw new ReferenceNotFoundException

  override def delete(user: User, timestamp: Long)(path: Path)(implicit references: References) = path match {
    case Path(MatchNamespace(x) :: Nil) if namespace contains x=> copy(namespace=namespace - x)(nodeMeta.modify(user, timestamp))
    case Path(MatchNamespace(x) :: xs) if namespace contains x => this.copy(namespace=namespace + (x -> namespace(x).delete(user, timestamp)(Path(xs))))(nodeMeta.modify(user, timestamp))
    case Path(Match(predicate) :: xs) => this.copy(namespace=namespace ++ namespace.filter({case(name, node) => predicate.matches(node)}).map(t => t._1 -> t._2.delete(user, timestamp)(Path(xs))))(nodeMeta.modify(user, timestamp))
    case Path(WildCardMatch :: Nil) => this.copy(namespace = Map.empty)(nodeMeta.modify(user, timestamp))
    case Path(WildCardMatch :: xs) => this.copy(namespace = namespace.map(t => t._1 -> t._2.delete(user, timestamp)(Path(xs))))(nodeMeta.modify(user, timestamp))
    case Path(SelectKeys(keys) :: Nil) => this.copy(namespace=namespace -- keys)(nodeMeta.modify(user, timestamp))
    case Path(SelectKeys(keys) :: xs) => this.copy(namespace=namespace ++ keys.filter(namespace contains).map(key => key -> namespace(key).delete(user, timestamp)(Path(xs))).toMap)(nodeMeta.modify(user, timestamp))
    case path => throw new PathNotFoundException(path)
  }

  override def addDefault(at:Path, user: User, timestamp: Long)(path:Path,value:Node)(implicit references: References) = at match {
    case Path(Nil) => this.copy(defaults = defaults + (path -> value))(nodeMeta.modify(user, timestamp))
    case Path(WildCardMatch ::  xs) => this.copy(namespace = namespace.map(t => t._1 -> t._2.addDefault(Path(xs), user, timestamp)(path,value)))(nodeMeta.modify(user, timestamp))
    case Path(SelectKeys(keys) ::  xs) => this.copy(namespace = namespace ++ keys.filter(namespace contains).map(key => key -> namespace(key).addDefault(Path(xs), user, timestamp)(path,value)))(nodeMeta.modify(user, timestamp))
    case Path(MatchNamespace(x) :: xs) => this.copy(namespace = namespace + (x -> namespace.getOrElse(x,Namespace()(NodeMeta(user, timestamp))).addDefault(at.rest, user, timestamp)(path,value)))(nodeMeta.modify(user, timestamp))
    case _ => throw PathNotFoundException(at)
  }

  override def deleteDefault(at: Path, user: User, timestamp: Long)(path: Path)(implicit references: References): Node = at match {
    case Path(Nil) => this.copy(defaults = defaults - path)(nodeMeta.modify(user, timestamp))
    case Path(WildCardMatch :: xs) => this.copy(namespace = namespace.map(t => t._1 -> t._2.deleteDefault(Path(xs), user, timestamp)(path)))(nodeMeta.modify(user, timestamp))
    case Path(SelectKeys(keys) :: xs) => this.copy(namespace = namespace ++ keys.filter(namespace contains).map(key => key -> namespace(key).deleteDefault(Path(xs), user, timestamp)(path)))(nodeMeta.modify(user, timestamp))
    case Path(MatchNamespace(x) :: xs) if namespace contains x => this.copy(namespace = namespace + (x -> namespace(x).deleteDefault(at.rest, user, timestamp)(path)))(nodeMeta.modify(user, timestamp))
    case _ => throw PathNotFoundException(at)
  }

  override def withDefaults(path: Path)(implicit references: References): Node = path match {
    case Path(WildCardMatch :: xs) => Namespace(namespace.map(t => t._1 -> t._2.withDefaults(Path(xs)))).addAllIfNotExist(defaults.all)
    case Path(SelectKeys(keys) :: xs) => Namespace(keys.filter(namespace contains).map(key => key -> namespace(key).withDefaults(Path(xs))).toMap).addAllIfNotExist(defaults.all)
    case Path(Match(predicate) :: xs) => Namespace(namespace.filter(entry => predicate.matches(entry._2)).map(t => t._1 -> t._2.withDefaults(Path(xs)))).addAllIfNotExist(defaults.all)
    case Path(MatchNamespace(x)::xs) if namespace contains x => Namespace(namespace + (x -> namespace(x).withDefaults(Path(xs)))).addAllIfNotExist(defaults.matchingName(x))
    case Path(MatchNamespace(x)::xs) => Namespace(namespace).addAll(defaults.matchingName(x))
    case Path(Nil) => Namespace(namespace.map(t => t._1 -> t._2.withDefaults(Path(Nil)))).addAllIfNotExist(defaults.all)
  }

  override def getDefaults(implicit references: References) = Some(defaults)

  override def addIfNotExist(value: Node)(path: Path)(implicit references: References) = path match {
    case Path(Nil) => value match {
      case Namespace(ns, d) => ns.foldLeft(this) { case (agg,v) =>
        agg.copy(namespace = agg.namespace + (v._1 -> namespace.getOrElse(v._1, v._2).addIfNotExist(v._2)(Path(Nil))))
      }
      case _ => this
    }
    case Path(MatchNamespace(x) :: Nil) if namespace contains x =>
      this.copy(namespace = namespace + (x -> namespace(x).addIfNotExist(value)(Path(Nil))))
    case Path(MatchNamespace(x) :: Nil) => this.copy(namespace = namespace + (x -> value))
    case Path(MatchNamespace(x) :: xs) => this.copy(namespace = namespace + (x -> namespace.getOrElse(x,Namespace()).addIfNotExist(value)(Path(xs))))
    case Path(WildCardMatch :: xs) => this.copy(namespace = namespace.map(t => t._1 -> t._2.addIfNotExist(value)(Path(xs))))
    case Path(SelectKeys(keys) :: xs) => this.copy(namespace = namespace ++ keys.filter(namespace contains).map(key => key -> namespace(key).addIfNotExist(value)(Path(xs))))
    case _ => this
  }

  override def keys(implicit references: References) = Some(this.namespace.keys.toList)

  override def extractRef(path: Path)(implicit references: References): Long = path match {
    case Path(MatchNamespace(x)::xs) if !namespace.contains(x) => throw new ReferenceNotFoundException
    case Path(MatchNamespace(x)::Nil) if !namespace(x).isReference => references.addRef(namespace(x))
    case Path(MatchNamespace(x)::xs) => namespace(x).extractRef(Path(xs))
    case _ => throw new ReferenceNotFoundException
  }
  @transient
  override lazy val allReferences = namespace.map(_._2.allReferences).foldLeft(Set.empty[Long])(_ ++ _)
}

@SerialVersionUID(2000L)
case class ListValue(value:List[Node])(implicit nodeMeta: NodeMeta) extends Node {

  override def getNodeMeta() = this.nodeMeta

  def performOnMatchingIndex(matchValue:Match[_])(perform:Function[Option[Int],Node])(implicit references: References) = {
    val where: Int = value.indexWhere(matchValue.filter.matches)
    if (where == -1) perform(None) else perform(Some(where))
  }

  def performOnIndex(index: Int)(perform: Function[Option[Int], Node]) =
    if (index < 0 || index > value.length) perform(None) else perform(Some(index))

  override def add(toAdd: Node, user: User, timestamp: Long)(path: Path)(implicit references: References) = path match {
    case Path(Nil) => toAdd
    case Path(WildCardMatch::xs) => ListValue(value.map(_.add(toAdd, user, timestamp)(Path(xs))))(this.nodeMeta.modify(user, timestamp))
    case path@Path((matchIndex: MatchIndex) :: xs) => performOnIndex(matchIndex.index){
      case Some(where) => if(where == value.length) ListValue(value = value :+ toAdd)(nodeMeta.modify(user, timestamp))
      else ListValue(value.updated(where, value(where).add(toAdd, user, timestamp)(path.rest)))(nodeMeta.modify(user, timestamp))
      case None => throw PathNotFoundException(path)
    }
    case path@Path((matcher:Match[_])::xs)   => performOnMatchingIndex(matcher){
      case Some(where) => ListValue(value.updated(where,value(where).add(toAdd, user, timestamp)(Path(xs))))(nodeMeta.modify(user, timestamp))
      case None => throw PathNotFoundException(path)
    }
    case path => throw IllegalOperationException(path, "Trying to access in lists")
  }

  override def patch(toAdd: Node, user: User, timestamp: Long)(path: Path)(implicit references: References) = path match {
    case Path(Nil) => toAdd
    case Path(WildCardMatch::xs) => ListValue(value.map(_.patch(toAdd, user, timestamp)(Path(xs))))(this.nodeMeta.modify(user, timestamp))
    case path@Path((matchIndex: MatchIndex) :: xs) => performOnIndex(matchIndex.index){
      case Some(where) => if(where == value.length) ListValue(value = value :+ toAdd)(nodeMeta.modify(user, timestamp))
      else ListValue(value.updated(where, value(where).add(toAdd, user, timestamp)(path.rest)))(nodeMeta.modify(user, timestamp))
      case None => throw PathNotFoundException(path)
    }
    case path@Path((matcher:Match[_])::xs)   => performOnMatchingIndex(matcher){
      case Some(where) => ListValue(value.updated(where,value(where).add(toAdd, user, timestamp)(Path(xs))))(nodeMeta.modify(user, timestamp))
      case None => throw PathNotFoundException(path)
    }
    case path => throw IllegalOperationException(path, "Trying to access in lists")
  }

  override def traverse(path: Path)(implicit references: References) = path match {
    case Path(Nil) => Some(this)
    case Path(WildCardMatch :: xs) => Some(ListValue(value.flatMap(_.traverse(Path(xs)))))
    case Path(Match(predicate) :: xs) => value.find(predicate.matches).flatMap(_.traverse(Path(xs)))
    case Path(MatchIndex(index) :: xs) =>
      if(index >= value.length || index < 0) None else value(index).traverse(Path(xs))
    case _ => None
  }

  override def asValue(implicit references: References) = value.map(_.asValue)

  override def asReferences(implicit references: References) = throw new ReferenceNotFoundException

  override def delete(user: User, timestamp: Long)(path: Path)(implicit references: References) = path match {
    case Path(WildCardMatch :: Nil) => ListValue(Nil)(nodeMeta.modify(user, timestamp))
    case Path(WildCardMatch :: xs) => ListValue(value.map(_.delete(user, timestamp)(Path(xs))))(nodeMeta.modify(user, timestamp))
    case path@Path((matchIndex: MatchIndex) :: xs) => performOnIndex(matchIndex.index)(deleteAt(path, user, timestamp))
    case path@Path((matcher:Match[_]) :: xs)  => performOnMatchingIndex(matcher){index: Option[Int] => deleteAt(path, user, timestamp)(index)}
    case path => throw IllegalOperationException(path, "Trying to access in lists")
  }

  def deleteAt(path: Path, user: User, timestamp: Long)(index: Option[Int])(implicit references: References):Node = index match {
    case Some(where) if where < value.length => if(path.path.tail == Nil) ListValue(value.patch(where, Nil, 1))(nodeMeta.modify(user, timestamp))
    else ListValue(value.updated(where, value(where).delete(user, timestamp)(path.rest)))(nodeMeta.modify(user, timestamp))
    case _ => throw PathNotFoundException(path)
  }

  override def withDefaults(path: Path)(implicit references: References): Node = path match {
    case Path(WildCardMatch::xs) => ListValue(value.map(_.withDefaults(path.rest)))
    case Path(Nil) => ListValue(value.map(_.withDefaults(Path(Nil))))
    case path@Path((matchIndex: MatchIndex) :: xs) => performOnIndex(matchIndex.index)(performWithDefaults(path))
    case path@Path((matcher:Match[_])::xs) => performOnMatchingIndex(matcher)(performWithDefaults(path))
    case _ => throw PathNotFoundException(path)
  }

  override def getDefaults(implicit references: References) = None

  def performWithDefaults(remainingPath: Path)(index: Option[Int])(implicit references: References)= index match {
    case Some(where) => ListValue(List(value(where).withDefaults(remainingPath.rest)))
    case None => ListValue(Nil)
  }

  override def addDefault(at: Path, user: User, timestamp: Long)(path: Path, toAdd: Node)(implicit references: References): Node = at match {
    case Path(WildCardMatch ::  xs) => ListValue(value.map(_.addDefault(Path(xs), user, timestamp)(path,toAdd)))(nodeMeta.modify(user, timestamp))
    case _ => throw IllegalOperationException(path, "Trying to add defaults through lists without wildcards")
  }

  override def deleteDefault(at: Path, user: User, timestamp: Long)(path: Path)(implicit references: References): Node = at match {
    case Path(WildCardMatch :: xs) => ListValue(value.map(_.deleteDefault(Path(xs), user, timestamp)(path)))(nodeMeta.modify(user, timestamp))
    case _ => throw IllegalOperationException(path, "Trying to delete defaults through list without wildcards")
  }

  override def addIfNotExist(toAdd: Node)(path: Path)(implicit references: References) = path match {
    case Path(WildCardMatch :: xs) => ListValue(value.map(_.addIfNotExist(toAdd)(Path(xs))))
    case Path((matcher:Match[_]) :: xs) => performOnMatchingIndex(matcher) {
      case Some(where) => if(xs == Nil) this else ListValue(value.updated(where, value(where).addIfNotExist(toAdd)(Path(xs))))
      case None => this
    }
    case _ => this
  }

  override def keys(implicit references: References) = None

  override def extractRef(path: Path)(implicit references: References) = path match {
    case path => throw IllegalOperationException(path, "Cannot extract reference from List")
  }
  @transient
  lazy val allReferences: Set[Long] = value.map(_.allReferences).foldLeft(Set.empty[Long])( _ ++ _ )

}

@SerialVersionUID(3000L)
case class Value[T](value: T)(implicit nodeMeta: NodeMeta) extends Node {

  override def getNodeMeta() = this.nodeMeta

  override def add(value: Node, user: User, timestamp: Long)(path: Path)(implicit references: References) = path match {
    case Path(Nil) => value
    case path => throw IllegalOperationException(path, "Trying to add value to value")
  }

  override def patch(value: Node, user: User, timestamp: Long)(path: Path)(implicit references: References) = path match {
    case Path(Nil) => value
    case path => throw IllegalOperationException(path, "Trying to add value to value")
  }

  override def traverse(path: Path) (implicit references: References) = path match {
    case Path(Nil) => Some(this)
    case _ => None
  }

  override def asValue(implicit references: References) = value

  override def asReferences(implicit references: References) = throw new ReferenceNotFoundException

  override def delete(user: User, timestamp: Long)(path:Path)(implicit references: References) = throw IllegalOperationException(path, "Trying to remove value from value")

  override def addDefault(at:Path, user: User, timestamp: Long)(path: Path, value: Node)(implicit references: References) = throw IllegalOperationException(path, "Cannot add defaults to a value")

  override def deleteDefault(at: Path, user: User, timestamp: Long)(path: Path)(implicit references: References) = throw IllegalOperationException(path, "Cannot delete defaults from a value")

  override def withDefaults(path: Path)(implicit references: References) = path match {
    case Path(Nil) => this
    case _ => throw PathNotFoundException(path)
  }

  override def getDefaults(implicit references: References) = None
  override def addIfNotExist(value: Node)(path: Path)(implicit references: References) = this

  override def keys(implicit references: References) = None

  override def extractRef(path: Path)(implicit references: References) = throw IllegalOperationException(path, "Cannot extract reference from Value")

  @transient
  override lazy val allReferences:Set[Long] = Set.empty
}

@SerialVersionUID(4000L)
case class Reference(val refKey: Long)(implicit nodeMeta: NodeMeta) extends Node {

  override def toString = "Ref( " + refKey + " )"
  override def getNodeMeta() = this.nodeMeta

  override def add(value: Node, user: User, timestamp: Long)(path: Path)(implicit references: References) = path match {
    case Path(Nil) => value
    case Path(remPath) => references.add(refKey, value, user, timestamp)(Path(remPath))
      this
  }

  override def patch(value: Node, user: User, timestamp: Long)(path: Path)(implicit references: References) = path match {
    case Path(remPath) => references.patch(refKey, value, user, timestamp)(Path(remPath))
      this
  }

  override def traverse(path: Path)(implicit references: References) = path match {
    case Path(Nil) => Some(this)
    case Path(remPath) => references.traverse(refKey)(Path(remPath))
  }

  override def withDefaults(remPath: Path)(implicit references: References) = this

  override def delete(user: User, timestamp: Long)(path: Path)(implicit references: References) = path match {
    case Path(remPath) => references.delete(refKey, user, timestamp)(Path(remPath))
      this
  }

  override def asValue(implicit references: References) = references.get(refKey).asValue

  override def asReferences(implicit references: References) = refKey

  override def addDefault(at:Path, user: User, timestamp: Long)(path:Path,value:Node)(implicit references: References) = {
    references.addDefault(refKey, at, user, timestamp)(path, value)
    this
  }

  override def addIfNotExist(value: Node)(path: Path)(implicit references: References) = {
    references.addIfNotExist(refKey, Map.empty)(path)
    this
  }

  override def getDefaults(implicit references: References) = references.getDefaults(refKey)

  override def keys(implicit references: References) = references.keys(refKey)

  override def deleteDefault(at: Path, user: User, timestamp: Long)(path: Path)(implicit references: References) = {
    references.deleteDefault(refKey, at, user, timestamp)(path)
    this
  }

  override def extractRef(path: Path)(implicit references: References) = path match {
    case Path(Nil) => refKey
    case Path(path) => references.extractRef(refKey)(Path(path))
  }

  override def isReference = true

  @transient
  lazy val allReferences: Set[Long] = Set(refKey)

}
object Tree {
  def apply(value:Any)(implicit nodeMeta: NodeMeta):Node = value match {
    case n:Map[_, _] => Namespace(namespace = n.filter(_._2 != null).map(t => t._1.toString -> Tree(t._2)))
    case v:List[_] => ListValue(value = v.filter(_ != null).map(Tree.apply(_)))
    case v => Value(v)
  }
  def updateNodeMeta(node: Node)(implicit nodeMeta: NodeMeta): Node = node match {
    case Namespace(namespace, defaults) => Namespace(namespace, defaults)
    case ListValue(items) => ListValue(items)
    case Value(v) => Value(v)
  }
}
