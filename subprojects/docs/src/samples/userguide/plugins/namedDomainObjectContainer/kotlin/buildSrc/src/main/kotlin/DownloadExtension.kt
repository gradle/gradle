
import javax.inject.Inject
import org.gradle.api.model.ObjectFactory
import org.gradle.api.NamedDomainObjectContainer

open class DownloadExtension @Inject constructor(objectFactory: ObjectFactory) {
    // Use an injected ObjectFactory to create a container of `Server` objects
    val servers = objectFactory.domainObjectContainer(Server::class.java)
}
