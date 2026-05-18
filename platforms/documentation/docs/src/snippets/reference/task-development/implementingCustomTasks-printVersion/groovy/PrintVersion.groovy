// tag::print-version[]
import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

abstract class PrintVersion extends DefaultTask {

    // Configuration code
    @Input
    abstract Property<String> getVersion()

    // Execution code
    @TaskAction
    void printVersion() {
        println("Version: ${getVersion().get()}")
    }
}
// end::print-version[]
