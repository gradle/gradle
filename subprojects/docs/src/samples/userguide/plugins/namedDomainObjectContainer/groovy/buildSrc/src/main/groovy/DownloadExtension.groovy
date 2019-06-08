import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.model.ObjectFactory

import javax.inject.Inject

class DownloadExtension {
    // A container of `Server` instances
    final NamedDomainObjectContainer<Server> servers

    @Inject
    DownloadExtension(ObjectFactory objectFactory) {
        // Use an injected ObjectFactory to create a Server object
        servers = objectFactory.domainObjectContainer(Server)
    }
}
