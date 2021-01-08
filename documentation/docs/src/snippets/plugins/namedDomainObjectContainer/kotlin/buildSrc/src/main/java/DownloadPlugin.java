import org.gradle.api.Plugin;
import org.gradle.api.Project;

public abstract class DownloadPlugin implements Plugin<Project> {
    public void apply(Project project) {
        project.getExtensions().create("download", DownloadExtension.class);
    }
}
