import org.gradle.api.Project
import org.gradle.api.Plugin

class DownloadPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.extensions.create("download", DownloadExtension::class.java)
    }
}
