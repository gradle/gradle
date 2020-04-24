import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

abstract class Download extends DefaultTask {
    // This property provides access to the service instance
    @Internal
    abstract Property<WebServer> getServer()

    @OutputFile
    abstract RegularFileProperty getOutputFile()

    @TaskAction
    void download() {
        // Use the server to download a file
        def server = server.get()
        def uri = server.uri.resolve("somefile.zip")
        println "Downloading $uri"
    }
}
