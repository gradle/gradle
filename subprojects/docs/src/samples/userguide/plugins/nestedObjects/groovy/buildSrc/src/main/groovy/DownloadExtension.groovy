import org.gradle.api.model.ObjectFactory

import javax.inject.Inject

class DownloadExtension {
    // A nested instance
    final Server server

    @Inject
    DownloadExtension(ObjectFactory objectFactory) {
        // Use an injected ObjectFactory to create a Server object
        server = objectFactory.newInstance(Server)
    }
}
