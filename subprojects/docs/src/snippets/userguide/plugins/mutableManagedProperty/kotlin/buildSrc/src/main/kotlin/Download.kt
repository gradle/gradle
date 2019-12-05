import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import java.net.URI

abstract class Download : DefaultTask() {
    // Use an abstract var
    @get:Input
    abstract var uri: URI

    @TaskAction
    fun run() {
        // Use the `uri` property
        println("Downloading $uri")
    }
}
