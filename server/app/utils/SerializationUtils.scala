package utils

import java.io._


object SerializationUtils {
  def serialize(value:Any) = {
    val baos: ByteArrayOutputStream = new ByteArrayOutputStream(512)
    val oos = new ObjectOutputStream(baos)
    try{oos.writeObject(value);baos.toByteArray}
    finally{oos.close()}
  }
  def deserialize[T](bytes:Array[Byte]) = {
    val ois = new ObjectInputStream(new ByteArrayInputStream(bytes)) {
      override def resolveClass(desc: java.io.ObjectStreamClass): Class[_] = {
        try { Class.forName(desc.getName, false, getClass.getClassLoader) }
          catch { case ex: ClassNotFoundException => super.resolveClass(desc) }
      }
    }
    try{ois.readObject().asInstanceOf[T]}
    finally{ois.close()}
  }
}
