import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.api.provider.Property
import org.gradle.api.file.DirectoryProperty

abstract class WebServer implements BuildService<Params>, AutoCloseable {

    // Some parameters for the web server
    interface Params extends BuildServiceParameters {
        Property<Integer> getPort()

        DirectoryProperty getResources()
    }

    // A public property for tasks to use
    final URI uri

    WebServer() {
        // Use the parameters
        def port = parameters.port.get()
        uri = new URI("https://localhost:$port/")

        // Start the server ...

        System.out.println("Server is running at $uri")
    }

    @Override
    void close() {
        // Stop the server ...
    }
}
