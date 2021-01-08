package org.gradle.sample.transform.javamodules;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.plugins.JavaPlugin;

/**
 * Entry point of our plugin that should be applied in the root project.
 */
public class ExtraModuleInfoPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        // register the plugin extension as 'extraJavaModuleInfo {}' configuration block
        ExtraModuleInfoPluginExtension extension = project.getObjects().newInstance(ExtraModuleInfoPluginExtension.class);
        project.getExtensions().add(ExtraModuleInfoPluginExtension.class, "extraJavaModuleInfo", extension);

        // setup the transform for all projects in the build
        project.getPlugins().withType(JavaPlugin.class).configureEach(javaPlugin -> configureTransform(project, extension));
    }

    private void configureTransform(Project project, ExtraModuleInfoPluginExtension extension) {
        Attribute<String> artifactType = Attribute.of("artifactType", String.class);
        Attribute<Boolean> javaModule = Attribute.of("javaModule", Boolean.class);

        // compile and runtime classpath express that they only accept modules by requesting the javaModule=true attribute
        project.getConfigurations().matching(this::isResolvingJavaPluginConfiguration).all(
                c -> c.getAttributes().attribute(javaModule, true));

        // all Jars have a javaModule=false attribute by default; the transform also recognizes modules and returns them without modification
        project.getDependencies().getArtifactTypes().getByName("jar").getAttributes().attribute(javaModule, false);

        // register the transform for Jars and "javaModule=false -> javaModule=true"; the plugin extension object fills the input parameter
        project.getDependencies().registerTransform(ExtraModuleInfoTransform.class, t -> {
            t.parameters(p -> {
                p.setModuleInfo(extension.getModuleInfo());
                p.setAutomaticModules(extension.getAutomaticModules());
            });
            t.getFrom().attribute(artifactType, "jar").attribute(javaModule, false);
            t.getTo().attribute(artifactType, "jar").attribute(javaModule, true);
        });
    }

    private boolean isResolvingJavaPluginConfiguration(Configuration configuration) {
        if (!configuration.isCanBeResolved()) {
            return false;
        }
        return configuration.getName().endsWith(JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME.substring(1))
                || configuration.getName().endsWith(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME.substring(1))
                || configuration.getName().endsWith(JavaPlugin.ANNOTATION_PROCESSOR_CONFIGURATION_NAME.substring(1));
    }
}
