package org.gradle.plugin.resolve.internal;

import org.gradle.api.Nullable;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.internal.artifacts.DependencyResolutionServices;
import org.gradle.internal.Factory;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.reflect.Instantiator;

public class ModuleMappingPluginResolver implements PluginResolver {

    private final String name;
    private final DependencyResolutionServices dependencyResolutionServices;
    private final Instantiator instantiator;
    private final Mapper mapper;

    public interface Mapper {
        @Nullable
        Dependency map(PluginRequest request, DependencyHandler dependencyHandler);
    }

    public ModuleMappingPluginResolver(String name, DependencyResolutionServices dependencyResolutionServices, Instantiator instantiator, Mapper mapper) {
        this.name = name;
        this.dependencyResolutionServices = dependencyResolutionServices;
        this.instantiator = instantiator;
        this.mapper = mapper;
    }

    public PluginResolution resolve(final PluginRequest pluginRequest) {
        final Dependency dependency = mapper.map(pluginRequest, dependencyResolutionServices.getDependencyHandler());
        if (dependency == null) {
            return null;
        } else {
            // TODO the dependency resolution config of this guy needs to be externalized
            Factory<ClassPath> classPathFactory = new DependencyResolvingClasspathProvider(dependencyResolutionServices, dependency);

            // TODO the classloader strategy employed here is naive - doesn't update the script classloader or reuse classes
            return new ClassPathPluginResolution(instantiator, pluginRequest.getId(), classPathFactory);
        }
    }

    public String getName() {
        return name;
    }

}
