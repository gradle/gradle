import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import org.gradle.api.provider.Property

abstract class UrlProcess extends DefaultTask {
    // Use an abstract getter method
    abstract Property<URI> getUri()

    @TaskAction
    void run() {
        // Use the `uri` property
        println "downloading ${uri.get()}"
    }
}
