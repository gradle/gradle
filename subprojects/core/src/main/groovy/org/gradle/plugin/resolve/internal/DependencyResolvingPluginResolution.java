package org.gradle.plugin.resolve.internal;

import org.gradle.api.Plugin;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.internal.artifacts.DependencyResolutionServices;
import org.gradle.api.internal.plugins.DefaultPluginRegistry;
import org.gradle.api.internal.plugins.PluginRegistry;
import org.gradle.internal.classpath.DefaultClassPath;
import org.gradle.internal.reflect.Instantiator;

import java.io.File;
import java.net.URLClassLoader;
import java.util.Set;

class DependencyResolvingPluginResolution implements PluginResolution {

    private final Dependency dependency;
    private final String pluginId;
    private final DependencyResolutionServices dependencyResolutionServices;
    private final Instantiator instantiator;

    public DependencyResolvingPluginResolution(DependencyResolutionServices dependencyResolutionServices, Instantiator instantiator, Dependency dependency, String pluginId) {
        this.dependencyResolutionServices = dependencyResolutionServices;
        this.dependency = dependency;
        this.pluginId = pluginId;
        this.instantiator = instantiator;
    }

    public Class<? extends Plugin> resolve(ClassLoader parentClassLoader) {
        Configuration configuration = dependencyResolutionServices.getConfigurationContainer().detachedConfiguration(dependency);
        dependencyResolutionServices.getResolveRepositoryHandler().jcenter(); // TODO remove this hardcoding
        Set<File> resolve = configuration.resolve();
        ClassLoader classLoader = new URLClassLoader(new DefaultClassPath(resolve).getAsURLArray(), parentClassLoader);
        PluginRegistry pluginRegistry = new DefaultPluginRegistry(classLoader, instantiator);
        Class<? extends Plugin> typeForId = pluginRegistry.getTypeForId(pluginId);
        return typeForId;
    }
}
