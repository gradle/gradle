
import java.util.Date

import play.api._
import java.io.{FileWriter, File}

object Global extends GlobalSettings {
  override def onStart(app: Application) = {
    // open for append
    val writer = new FileWriter(app.getFile("target/reload.log"), true)
    writer.write(new Date() + " - reloaded\n")
    writer.close()

    if (app.configuration.getBoolean("fail").getOrElse(false)) {
      throw new RuntimeException()
    }
  }
}