package org.gradle.sample.plugin;

import javax.inject.Inject;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.tooling.provider.model.ToolingModelBuilder;
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry;

/**
 * A plugin that exposes a custom tooling model.
 */
public class CustomPlugin implements Plugin<Project> {
    private final ToolingModelBuilderRegistry registry;

    /**
     * Need to use a {@link ToolingModelBuilderRegistry} to register the custom tooling model, so inject this into
     * the constructor.
     */
    @Inject
    public CustomPlugin(ToolingModelBuilderRegistry registry) {
        this.registry = registry;
    }

    public void apply(Project project) {
        // Register a builder for the custom tooling model
        registry.register(new CustomToolingModelBuilder());
    }

    private static class CustomToolingModelBuilder implements ToolingModelBuilder {
        public boolean canBuild(String modelName) {
            // The default name for a model is the name of the Java interface
            return modelName.equals(CustomModel.class.getName());
        }

        public Object buildAll(String modelName, Project project) {
            return new DefaultModel();
        }
    }
}
