import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.tasks.bundling.War;

// tag::snippet[]
public class InhouseConventionWarPlugin implements Plugin<Project> {
    public void apply(Project project) {
        project.getTasks().withType(War.class).configureEach(war ->
            war.getWebXml().set(project.file("src/someWeb.xml")));
    }
}
// end::snippet[]
