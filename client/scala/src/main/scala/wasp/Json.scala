package wasp

import com.fasterxml.jackson.core.`type`.TypeReference
import com.fasterxml.jackson.databind.{PropertyNamingStrategy, ObjectMapper}
import com.fasterxml.jackson.module.scala.DefaultScalaModule

object Json {
  lazy val mapper = {
    val result: ObjectMapper = new ObjectMapper()
    result.registerModule(DefaultScalaModule)
    result
  }

  def fromJson[T](content:String,clazz:Class[T]):T = mapper.readValue(content,clazz)
  def fromJson[T](content:String,typeRef:TypeReference[T]):T = mapper.readValue(content,typeRef)
  def toJson[T](value:T,pretty:Boolean = false) = if(pretty) mapper.writerWithDefaultPrettyPrinter().writeValueAsString(value) else mapper.writeValueAsString(value)
}
