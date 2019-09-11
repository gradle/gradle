import org.gradle.api.model.ObjectFactory

import javax.inject.Inject

class DownloadExtension {
    // A nested instance
    final Resource resource

    @Inject
    DownloadExtension(ObjectFactory objectFactory) {
        // Use an injected ObjectFactory to create a Resource object
        resource = objectFactory.newInstance(Resource)
    }
}
