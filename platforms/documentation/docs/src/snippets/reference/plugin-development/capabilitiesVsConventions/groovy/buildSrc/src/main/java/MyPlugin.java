import org.gradle.api.Plugin;
import org.gradle.api.Project;

public class MyPlugin implements Plugin<Project> {
    public void apply(Project project) {
        project.getPluginManager().apply(MyBasePlugin.class);

        // define conventions
    }
}
