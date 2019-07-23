import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.TaskAction

// tag::url-process[]
abstract class UrlProcess : DefaultTask() {
    // Use an abstract getter method annotated with @Nested
    @get:Nested
    abstract val hostAndPath: HostAndPath

    @TaskAction
    fun run() {
        // Use the `hostAndPath` property
        println("Downloading https://${hostAndPath.hostName.get()}/${hostAndPath.path.get()}")
    }
}

interface HostAndPath {
    @get:Input
    val hostName: Property<String>
    @get:Input
    val path: Property<String>
}
// end::url-process[]
