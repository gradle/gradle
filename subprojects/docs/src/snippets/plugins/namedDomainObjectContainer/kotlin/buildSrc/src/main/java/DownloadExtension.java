import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.model.ObjectFactory;

import javax.inject.Inject;

public class DownloadExtension {
    // A container of `Resource` objects
    private final NamedDomainObjectContainer<Resource> resources;

    @Inject
    public DownloadExtension(ObjectFactory objectFactory) {
        // Use an injected ObjectFactory to create a container
        resources = objectFactory.domainObjectContainer(Resource.class);
    }

    public NamedDomainObjectContainer<Resource> getResources() {
        return resources;
    }
}
