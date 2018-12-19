package security

import com.indix.wasp.models.User
import play.api.Configuration

import scala.collection.JavaConverters._

object WaspPermissions {

   import security.Access._

   def checkIfUserExists(config: Configuration, permission: Configuration, user: User): Boolean = {
     val roles = config.getConfig("roles")
     val users = permission.getStringList("roles").get.asScala.map { permittedRole =>
       roles.get.getStringList(permittedRole).get.asScala
     }.flatten
     users.contains(user.email)
   }

   def getPermissionForPath(config: Configuration, path: String): Option[Configuration] = {
     val permissions = config.getConfigList("permissions")
     permissions.get.asScala.find { permission =>
       path.matches(permission.getString("path").get)
     }
   }

   def isUserPermitted(config: Configuration, user: User, path: String, requestMethod: String): Boolean = {

     if (user.isAdmin)
       return true

     val permission: Option[Configuration] = getPermissionForPath(config, path)
     if (permission.isEmpty)
       return true

     val accessGroup = if (checkIfUserExists(config, permission.get, user)) "self" else "others"
     val accessRights = permission.get.getString(accessGroup).get
     val accessType = if (requestMethod == "GET") READ else if (List("POST", "DELETE", "PUT").contains(requestMethod)) WRITE
     if ((accessRights != "") && List(WRITE, accessType).contains(Access.withName(accessRights)))
       true
     else
       false
   }
 }