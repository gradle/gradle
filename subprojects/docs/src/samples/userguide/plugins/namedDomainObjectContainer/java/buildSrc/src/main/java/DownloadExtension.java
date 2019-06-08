import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.model.ObjectFactory;

import javax.inject.Inject;

public class DownloadExtension {
    // A container of `Server` objects
    private final NamedDomainObjectContainer<Server> servers;

    @Inject
    public DownloadExtension(ObjectFactory objectFactory) {
        // Use an injected ObjectFactory to create a container
        servers = objectFactory.domainObjectContainer(Server.class);
    }

    public NamedDomainObjectContainer<Server> getServers() {
        return servers;
    }
}
