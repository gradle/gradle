import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.provider.Property;

public class NebulaPluginPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        project.getPlugins().apply(GradlePluginPlugin.class);
        project.getPlugins().apply(PublishPlugin.class);

        NebulaPluginExtension nebulaPlugin = project.getExtensions().create("nebulaPlugin", NebulaPluginExtension.class, project.getObjects().property(String.class));
        GradlePluginPlugin.PluginDevelopmentExtension pluginDevelopmentExtension = project.getExtensions().getByType(GradlePluginPlugin.PluginDevelopmentExtension.class);
        pluginDevelopmentExtension.getAutoPublish().set(true);

        project.afterEvaluate(evaluatedProject -> {
            pluginDevelopmentExtension.getPlugins().create(nebulaPlugin.getName().get());
        });
    }

    public static class NebulaPluginExtension {
        private final Property<String> name;

        public NebulaPluginExtension(Property<String> name) {
            this.name = name;
        }

        public Property<String> getName() {
            return name;
        }
    }
}
