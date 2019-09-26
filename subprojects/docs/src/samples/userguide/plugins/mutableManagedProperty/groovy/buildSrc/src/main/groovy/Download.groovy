import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

abstract class Download extends DefaultTask {
    // Use an abstract property
    abstract URI uri

    @TaskAction
    void run() {
        // Use the `uri` property
        println "downloading ${uri}"
    }
}
