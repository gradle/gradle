import org.gradle.api.Named;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.ExtensionContainer;
import org.gradle.api.provider.Property;

public class GradlePluginPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {

        ExtensionContainer.ExtensionProvider<PluginDevelopmentExtension> pluginDevelopmentProvider = project.getExtensions().register(PluginDevelopmentExtension.class, "pluginDevelopment", PluginDevelopmentExtension.class, project.getObjects().property(Boolean.class), project.container(GradlePlugin.class));

        project.getPlugins().withType(PublishPlugin.class, (PublishPlugin plugin) -> {
            ExtensionContainer.ExtensionProvider<PublishPlugin.PublishingExtension> publishingExtension = project.getExtensions().typed(PublishPlugin.PublishingExtension.class);
            publishingExtension.configure((PublishPlugin.PublishingExtension extension) -> {
                PluginDevelopmentExtension pluginDevelopmentExtension = pluginDevelopmentProvider.get();
                if (pluginDevelopmentExtension.autoPublish.get()) {
                    pluginDevelopmentExtension.plugins.forEach(gradlePlugin -> {
                        extension.getPublications().create("maven" + gradlePlugin.getName());
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
