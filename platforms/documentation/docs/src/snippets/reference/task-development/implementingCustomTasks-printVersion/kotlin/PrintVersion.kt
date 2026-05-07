// tag::print-version[]
import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

abstract class PrintVersion : DefaultTask() {

    // Configuration code
    @get:Input
    abstract val version: Property<String>

    // Execution code
    @TaskAction
    fun print() {
        println("Version: ${version.get()}")
    }
}
// end::print-version[]
