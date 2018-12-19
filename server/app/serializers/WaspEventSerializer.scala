package serializers

import akka.serialization.Serializer
import com.esotericsoftware.kryo.serializers.CompatibleFieldSerializer
import com.indix.wasp.models.User
import com.twitter.chill._
import models._

class WaspEventSerializer extends Serializer {

  val MODEL_CLASSES = List(classOf[WaspEvent], classOf[Path], classOf[PathComponentMatcher], classOf[Node],
                           classOf[Defaults], classOf[NodeMeta], classOf[User], classOf[Command])

  def kryoInstantiator: KryoInstantiator = new ScalaKryoInstantiator {
    override def newKryo = {
      val kryo: KryoBase = super.newKryo()
      MODEL_CLASSES
        .foreach{clazz =>  kryo.register(clazz, new CompatibleFieldSerializer(kryo, clazz))}
      kryo
    }
  }

  def poolSize: Int = {
    val GUESS_THREADS_PER_CORE = 4
    GUESS_THREADS_PER_CORE * Runtime.getRuntime.availableProcessors
  }

  val kryoPool: KryoPool =
    KryoPool.withByteArrayOutputStream(poolSize, kryoInstantiator)

  def includeManifest: Boolean = false
  def identifier = 9876543
  def toBinary(obj: AnyRef): Array[Byte] =
    kryoPool.toBytesWithClass(obj)

  def fromBinary(bytes: Array[Byte], clazz: Option[Class[_]]): AnyRef =
    kryoPool.fromBytes(bytes)
}