import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import org.gradle.api.provider.Property
import java.net.URI

abstract class UrlProcess : DefaultTask() {
    // Use an abstract val
    abstract val uri: Property<URI>

    @TaskAction
    fun run() {
        // Use the `uri` property
        println("Downloading ${uri.get()}")
    }
}
