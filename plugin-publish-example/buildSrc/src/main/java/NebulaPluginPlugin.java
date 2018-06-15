import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.ExtensionContainer;
import org.gradle.api.provider.Property;

public class NebulaPluginPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        project.getPlugins().apply(GradlePluginPlugin.class);
        project.getPlugins().apply(PublishPlugin.class);

        ExtensionContainer.ExtensionProvider<NebulaPluginExtension> nebulaProvider = project.getExtensions().register(NebulaPluginExtension.class, "nebulaProvider", NebulaPluginExtension.class, project.getObjects().property(String.class));
        ExtensionContainer.ExtensionProvider<GradlePluginPlugin.PluginDevelopmentExtension> pluginDevelopmentExtensionProvider = project.getExtensions().typed(GradlePluginPlugin.PluginDevelopmentExtension.class);
        pluginDevelopmentExtensionProvider.configure(pluginDevelopmentExtension -> {
            pluginDevelopmentExtension.getAutoPublish().set(true);
            pluginDevelopmentExtension.getPlugins().create(nebulaProvider.get().getName().get());
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
