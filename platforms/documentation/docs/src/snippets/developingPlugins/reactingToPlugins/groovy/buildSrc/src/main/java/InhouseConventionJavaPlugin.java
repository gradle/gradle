import java.util.Arrays;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;

// tag::snippet[]
public class InhouseConventionJavaPlugin implements Plugin<Project> {
    public void apply(Project project) {
        project.getPlugins().withType(JavaPlugin.class, javaPlugin -> {
            SourceSetContainer sourceSets = project.getExtensions().getByType(SourceSetContainer.class);
            SourceSet main = sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME);
            main.getJava().setSrcDirs(Arrays.asList("src"));
        });
    }
}
// end::snippet[]
