import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.api.provider.Property
import org.gradle.api.file.DirectoryProperty
import java.net.URI

abstract class WebServer : BuildService<WebServer.Params>, AutoCloseable {

    // Some parameters for the web server
    interface Params : BuildServiceParameters {
        val port: Property<Int>
        val resources: DirectoryProperty
    }

    // A public property for tasks to use
    val uri: URI

    init {
        // Use the parameters
        val port = parameters.port.get()
        uri = URI("https://localhost:$port/")

        // Start the server ...

        println("Server is running at $uri")
    }

    override fun close() {
        // Stop the server ...
    }
}
