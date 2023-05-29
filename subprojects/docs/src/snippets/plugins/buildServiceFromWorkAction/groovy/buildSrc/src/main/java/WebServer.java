import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;

import java.net.URI;
import java.net.URISyntaxException;

public abstract class WebServer implements BuildService<WebServer.Params>, AutoCloseable {

    // Some parameters for the web server
    interface Params extends BuildServiceParameters {
        Property<Integer> getPort();

        DirectoryProperty getResources();
    }

    private final URI uri;

    public WebServer() throws URISyntaxException {
        // Use the parameters
        int port = getParameters().getPort().get();
        uri = new URI(String.format("https://localhost:%d/", port));

        // Start the server ...

        System.out.println(String.format("Server is running at %s", uri));
    }

    // A public method for tasks to use
    public URI getUri() {
        return uri;
    }

    @Override
    public void close() {
        // Stop the server ...
    }
}
