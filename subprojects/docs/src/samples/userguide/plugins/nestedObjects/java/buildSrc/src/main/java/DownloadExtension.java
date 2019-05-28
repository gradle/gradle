import org.gradle.api.model.ObjectFactory;

import javax.inject.Inject;

public class DownloadExtension {
    // A nested instance
    private final Server server;

    @Inject
    public DownloadExtension(ObjectFactory objectFactory) {
        // Use an injected ObjectFactory to create a Server object
        server = objectFactory.newInstance(Server.class);
    }

    public Server getServer() {
        return server;
    }
}
