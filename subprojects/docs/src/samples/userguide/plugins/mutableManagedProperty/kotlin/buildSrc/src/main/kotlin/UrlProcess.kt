
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import java.net.URI

abstract class UrlProcess : DefaultTask() {
    // Use an abstract var
    abstract var uri: URI

    @TaskAction
    fun run() {
        // Use the `uri` property
        println("Downloading $uri")
    }
}
