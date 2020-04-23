import org.gradle.api.Plugin
import org.gradle.api.Project

class DownloadPlugin implements Plugin<Project> {
    void apply(Project project) {
        project.extensions.create('download', DownloadExtension)
    }
}
