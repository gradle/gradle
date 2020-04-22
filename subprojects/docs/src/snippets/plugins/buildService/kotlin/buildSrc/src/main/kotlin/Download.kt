import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

abstract class Download : DefaultTask() {
    // This property provides access to the service instance
    @get:Internal
    abstract val server: Property<WebServer>

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun download() {
        // Use the server to download a file
        val server = server.get()
        val uri = server.uri.resolve("somefile.zip")
        println("Downloading $uri")
    }
}
