import org.gradle.api.Project
import org.gradle.api.Plugin
import org.gradle.api.provider.Provider

class DownloadPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        // Register the service
        val serviceProvider = project.gradle.sharedServices.registerIfAbsent("web", WebServer::class.java) {
            // Provide some parameters
            it.parameters.port.set(5005)
        }

        project.tasks.register("download", Download::class.java) {
            // Connect the service provider to the task
            it.server.set(serviceProvider)
            it.outputFile.set(project.layout.buildDirectory.file("result.zip"))
        }
    }
}
