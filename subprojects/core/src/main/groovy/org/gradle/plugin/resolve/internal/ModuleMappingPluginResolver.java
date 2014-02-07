/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.plugin.resolve.internal;

import org.gradle.api.Action;
import org.gradle.api.Nullable;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.internal.artifacts.DependencyResolutionServices;
import org.gradle.internal.Factory;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.reflect.Instantiator;

public abstract class ModuleMappingPluginResolver implements PluginResolver {

    private final String name;
    private final DependencyResolutionServices dependencyResolutionServices;
    private final Instantiator instantiator;
    private final Mapper mapper;
    private Action<? super RepositoryHandler> repositoriesConfigurer;

    public interface Mapper {
        @Nullable
        Dependency map(PluginRequest request, DependencyHandler dependencyHandler);
    }

    public ModuleMappingPluginResolver(String name, DependencyResolutionServices dependencyResolutionServices, Instantiator instantiator, Mapper mapper, Action<? super RepositoryHandler> repositoriesConfigurer) {
        this.name = name;
        this.dependencyResolutionServices = dependencyResolutionServices;
        this.instantiator = instantiator;
        this.mapper = mapper;
        this.repositoriesConfigurer = repositoriesConfigurer;
    }

    public PluginResolution resolve(final PluginRequest pluginRequest) {
        final Dependency dependency = mapper.map(pluginRequest, dependencyResolutionServices.getDependencyHandler());
        if (dependency == null) {
            return null;
        } else {
            // TODO the dependency resolution config of this guy needs to be externalized
            Factory<ClassPath> classPathFactory = new DependencyResolvingClasspathProvider(dependencyResolutionServices, dependency, repositoriesConfigurer);

            return new ClassPathPluginResolution(instantiator, pluginRequest.getId(), classPathFactory);
        }
    }

    @Override
    public String toString() {
        return getClass().getName() + "[" + name + "]";
    }

    public abstract String getDescriptionForNotFoundMessage();
}
