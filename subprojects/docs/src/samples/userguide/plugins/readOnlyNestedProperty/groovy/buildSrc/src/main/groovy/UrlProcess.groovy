import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.TaskAction

// tag::url-process[]
abstract class UrlProcess extends DefaultTask {
    // Use an abstract getter method annotated with @Nested
    @Nested
    abstract HostAndPath getHostAndPath()

    @TaskAction
    void run() {
        // Use the `hostAndPath` property
        println("Downloading https://${hostAndPath.hostName.get()}/${hostAndPath.path.get()}")
    }
}

interface HostAndPath {
    @Input
    Property<String> getHostName()
    @Input
    Property<String> getPath()
}
// end::url-process[]
