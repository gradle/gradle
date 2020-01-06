import org.gradle.api.Plugin
import org.gradle.api.Project

class DownloadPlugin implements Plugin<Project> {
    void apply(Project project) {
        // Register the service
        def serviceProvider = project.gradle.sharedServices.registerIfAbsent("web", WebServer) {
            // Provide some parameters
            parameters.port = 5005
        }

        project.tasks.register("download", Download) {
            // Connect the service provider to the task
            server = serviceProvider
            outputFile = project.layout.buildDirectory.file('result.zip')
        }
    }
}
