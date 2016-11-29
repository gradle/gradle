/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.plugin.use.resolve.service.internal;

import org.gradle.api.GradleException;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ResolveException;
import org.gradle.api.internal.artifacts.DependencyResolutionServices;
import org.gradle.api.internal.artifacts.configurations.ConfigurationContainerInternal;
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;
import org.gradle.api.internal.initialization.ClassLoaderScope;
import org.gradle.api.internal.plugins.PluginInspector;
import org.gradle.api.specs.Specs;
import org.gradle.internal.Factories;
import org.gradle.internal.Factory;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.classpath.DefaultClassPath;
import org.gradle.internal.exceptions.Contextual;
import org.gradle.plugin.internal.DefaultPluginId;
import org.gradle.plugin.use.resolve.internal.ClassPathPluginResolution;
import org.gradle.plugin.use.resolve.internal.PluginResolution;

import java.io.File;
import java.util.Set;

public class ResolutionServiceResolver {

    private final Factory<DependencyResolutionServices> dependencyResolutionServicesFactory;
    private final ClassLoaderScope parentScope;
    private final PluginInspector pluginInspector;

    public ResolutionServiceResolver(
        ClassLoaderScope parentScope, Factory<DependencyResolutionServices> dependencyResolutionServicesFactory, PluginInspector pluginInspector
    ) {
        this.parentScope = parentScope;
        this.dependencyResolutionServicesFactory = dependencyResolutionServicesFactory;
        this.pluginInspector = pluginInspector;
    }

    PluginResolution buildPluginResolution(DefaultPluginId id, Factory<? extends ClassPath> classPathFactory) {
        return new ClassPathPluginResolution(id, parentScope, classPathFactory, pluginInspector);
    }

    ClassPath resolvePluginDependencies(final Object dependencyNotation, final String location) {
        DependencyResolutionServices resolution = dependencyResolutionServicesFactory.create();
        Dependency dependency = resolution.getDependencyHandler().create(dependencyNotation);

        ConfigurationContainerInternal configurations = (ConfigurationContainerInternal) resolution.getConfigurationContainer();
        ConfigurationInternal configuration = configurations.detachedConfiguration(dependency);

        try {
            Set<File> files = configuration.getResolvedConfiguration().getFiles(Specs.satisfyAll());
            return new DefaultClassPath(files);
        } catch (ResolveException e) {
            throw new DependencyResolutionException("Failed to resolve all plugin dependencies from " + location, e.getCause());
        }
    }

    public DependencyResolutionServices getDependencyResolutionServices() {
        return dependencyResolutionServicesFactory.create();
    }

    @Contextual
    public static class DependencyResolutionException extends GradleException {
        public DependencyResolutionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
