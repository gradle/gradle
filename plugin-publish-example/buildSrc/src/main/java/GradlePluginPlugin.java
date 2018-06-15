import org.gradle.api.Named;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.provider.Property;

public class GradlePluginPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {

        PluginDevelopmentExtension pluginDevelopment = project.getExtensions().create("pluginDevelopment", PluginDevelopmentExtension.class, project.getObjects().property(Boolean.class), project.container(GradlePlugin.class));

        project.getPlugins().withType(PublishPlugin.class, (PublishPlugin plugin) -> {
            project.afterEvaluate(evaluatedProject -> {
                PublishPlugin.PublishingExtension publishingExtension = project.getExtensions().getByType(PublishPlugin.PublishingExtension.class);
                if (pluginDevelopment.autoPublish.get()) {
                    pluginDevelopment.plugins.forEach(gradlePlugin -> {
                        publishingExtension.getPublications().create("maven" + gradlePlugin.getName());
                    });
                }
            });
        });
    }

    public static class PluginDevelopmentExtension {
        private final Property<Boolean> autoPublish;
        private final NamedDomainObjectContainer<GradlePlugin> plugins;

        public PluginDevelopmentExtension(Property<Boolean> autoPublish, NamedDomainObjectContainer<GradlePlugin> plugins) {
            this.autoPublish = autoPublish;
            this.plugins = plugins;
        }

        public Property<Boolean> getAutoPublish() {
            return autoPublish;
        }

        public NamedDomainObjectContainer<GradlePlugin> getPlugins() {
            return plugins;
        }
    }

    public static class GradlePlugin implements Named {
        private final String name;

        public GradlePlugin(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }
    }
}
