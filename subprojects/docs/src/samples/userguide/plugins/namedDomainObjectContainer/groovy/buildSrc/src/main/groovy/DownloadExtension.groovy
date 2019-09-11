import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.model.ObjectFactory

import javax.inject.Inject

class DownloadExtension {
    // A container of `Resource` instances
    final NamedDomainObjectContainer<Resource> resources

    @Inject
    DownloadExtension(ObjectFactory objectFactory) {
        // Use an injected ObjectFactory to create a container
        resources = objectFactory.domainObjectContainer(Resource)
    }
}
