import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.api.provider.Property
import java.net.URI

abstract class Download : DefaultTask() {
    // Use an abstract val
    @get:Input
    abstract val uri: Property<URI>

    @TaskAction
    fun run() {
        // Use the `uri` property
        println("Downloading ${uri.get()}")
    }
}
