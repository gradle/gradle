import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.TaskAction

// tag::download[]
abstract class Download : DefaultTask() {
    // Use an abstract getter method annotated with @Nested
    @get:Nested
    abstract val resource: Resource

    @TaskAction
    fun run() {
        // Use the `resource` property
        println("Downloading https://${resource.hostName.get()}/${resource.path.get()}")
    }
}

interface Resource {
    @get:Input
    val hostName: Property<String>
    @get:Input
    val path: Property<String>
}
// end::download[]
