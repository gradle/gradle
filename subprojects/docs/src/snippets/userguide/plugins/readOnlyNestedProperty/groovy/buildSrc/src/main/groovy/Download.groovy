import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.TaskAction

// tag::download[]
abstract class Download extends DefaultTask {
    // Use an abstract getter method annotated with @Nested
    @Nested
    abstract Resource getResource()

    @TaskAction
    void run() {
        // Use the `resource` property
        println("Downloading https://${resource.hostName.get()}/${resource.path.get()}")
    }
}

interface Resource {
    @Input
    Property<String> getHostName()
    @Input
    Property<String> getPath()
}
// end::download[]
