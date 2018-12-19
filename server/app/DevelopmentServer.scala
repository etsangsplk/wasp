import java.io.File

import play.api.Mode
import play.core.StaticApplication
import play.core.server.NettyServer

object DevelopmentServer {
  def main(args: Array[String]): Unit = {
    // by default the only argument you need is "."
    val applicationPath = new File(args(0))
    val server = new NettyServer(
      new StaticApplication(applicationPath),
      Option(System.getProperty("http.port")).fold(Option(9000))(p => if (p == "disabled") Option.empty[Int] else Option(Integer.parseInt(p))),
      Option(System.getProperty("https.port")).map(Integer.parseInt(_)),
      Option(System.getProperty("http.address")).getOrElse("0.0.0.0"),
      Mode.Dev
    )

    Runtime.getRuntime.addShutdownHook(new Thread {
      override def run {
        server.stop()
      }
    })
  }

}
