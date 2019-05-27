import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

abstract class UrlProcess extends DefaultTask {
    // Use an abstract property method
    abstract URI uri

    @TaskAction
    void run() {
        // Use the `uri` property
        println "downloading ${uri}"
    }
}
