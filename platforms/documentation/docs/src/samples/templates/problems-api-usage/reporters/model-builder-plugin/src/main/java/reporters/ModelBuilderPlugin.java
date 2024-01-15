package reporters;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.model.ObjectFactory;
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry;

import javax.inject.Inject;

public class ModelBuilderPlugin implements Plugin<Project> {

    private final ToolingModelBuilderRegistry registry;

    @Inject
    public ModelBuilderPlugin(ObjectFactory factory, ToolingModelBuilderRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void apply(Project target) {
        ModelBuilder modelBuilder = target.getObjects().newInstance(ModelBuilder.class);
        registry.register(modelBuilder);
    }
}
