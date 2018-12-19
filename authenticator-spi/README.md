# Authenticator-spi

**Authenticator-spi** is an interface project that needs to implemented by the authentication plugin. The authentication plugin is used by the **wasp-server** to authenticate the incoming requests. The wasp-server uses [ServiceLoader](https://docs.oracle.com/javase/tutorial/ext/basics/spi.html) to load the implementation of `AuthenticationProvider` interface of **authenticator-spi** from the plugin.



### Using Authenticator-spi for creating authentication plugin

1. Add the configuration needed for your authentication plugin in `/wasp/server/conf/application.conf`. For example, the **sample-wasp-authenticator** adds the `file-path` needed by the plugin where all valid credentials are stored:

```
auth {
  file-path = "/Users/indix/wasp/sample-wasp-authenticator/src/main/resources/sample_passwd"
}
```

2. Add the **authenticator-spi** project as dependency in your plugin project. You can create a .jar of authenticator-spi project and add it to the classpath of your plugin project.

3. Implement the `AuthenticatorPlugin` interface and the `AuthenticatorPluginProvider` interface. The `create(config: Configuration)` method is a factory which returns the implementation of `AuthenticationPlugin` interface. The `config` object contains the configuration passed in **Step 1** which can be used for implementing the interface methods.

   The `authenticate()` method takes the incoming request and returns the `Future[User]` if authentication is successful and throws `AuthenticationErrorException` if the authentication fails :

```
override def authenticate[A](request: Request[A])(implicit executionContext: ExecutionContext): Future[User] = {
// return the Future[User] of authenticated user
}
```


   The `defaultUser()` method returns the default user to be returned when the authentication fails :   


```
override def defaultUser(implicit executionContext: ExecutionContext): User = {
}
```

   You can refer to **sample-wasp-authenticator** plugin as an example.

4. Create the `resources/META-INF/services/` directory and a file named after the full package name of the interface of authenticator-spi inside it. For example :

```
Filename : resources/META-INF/services/com.indix.wasp.authentication.AuthenticatorProvider
```

   The first line in this file should contain the full package name of the class containing the implementation of `AuthenticationProvider` interface. For example :  

```
com.indix.wasp.authentication.simple.AuthenticatorPluginProvider
```

5. Package your plugin as a .jar and add it to classpath of the **wasp/server** project. The `AuthenticationService` class will load the provided implementation in plugin using the [ServiceLoader](https://docs.oracle.com/javase/tutorial/ext/basics/spi.html). 

