package utils

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule

object JsonUtils {

  val mapper = {
    val result = new ObjectMapper
    result.registerModule(DefaultScalaModule)
    result
  }

  def fromJson[T](value:String,clazz:Class[T]):T = mapper.readValue(value,clazz)
  def fromJson(value:String) = mapper.readValue(value,classOf[Object])
  def toJson[T](value:T, pretty: Boolean = false) =
    if(pretty) mapper.writerWithDefaultPrettyPrinter().writeValueAsString(value)
    else mapper.writeValueAsString(value)
}
