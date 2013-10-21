package org.gradle.plugin.resolve.internal;

import org.gradle.api.Nullable;
import org.gradle.api.Plugin;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.internal.artifacts.DependencyResolutionServices;
import org.gradle.api.internal.plugins.DefaultPluginRegistry;
import org.gradle.api.internal.plugins.PluginRegistry;
import org.gradle.internal.classpath.DefaultClassPath;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.plugin.resolve.PluginRequest;
import org.gradle.plugin.resolve.PluginResolution;
import org.gradle.plugin.resolve.PluginResolver;

import java.io.File;
import java.net.URLClassLoader;
import java.util.Set;

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
            return new PluginResolution() {
                public Class<? extends Plugin> resolve(ClassLoader parentClassLoader) {
                    Configuration configuration = dependencyResolutionServices.getConfigurationContainer().detachedConfiguration(dependency);
                    dependencyResolutionServices.getResolveRepositoryHandler().jcenter(); // TODO remove this hardcoding
                    Set<File> resolve = configuration.resolve();
                    ClassLoader classLoader = new URLClassLoader(new DefaultClassPath(resolve).getAsURLArray(), parentClassLoader);
                    PluginRegistry pluginRegistry = new DefaultPluginRegistry(classLoader, instantiator);
                    Class<? extends Plugin> typeForId = pluginRegistry.getTypeForId(pluginRequest.getId());
                    return typeForId;
                }
            };
        }
    }

    public String getName() {
        return name;
    }
}
