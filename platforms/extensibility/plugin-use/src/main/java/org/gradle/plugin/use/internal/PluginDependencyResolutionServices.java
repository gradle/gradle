/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.plugin.use.internal;

import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.internal.artifacts.DependencyResolutionServices;
import org.gradle.api.internal.artifacts.dsl.RepositoryHandlerInternal;
import org.gradle.internal.Factory;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.gradle.plugin.use.resolve.internal.PluginArtifactRepositories;
import org.gradle.plugin.use.resolve.internal.PluginArtifactRepositoriesProvider;

@ServiceScope(Scope.Build.class)
public class PluginDependencyResolutionServices implements PluginRepositoryHandlerProvider, PluginArtifactRepositoriesProvider {
    private final Factory<DependencyResolutionServices> factory;
    private DependencyResolutionServices dependencyResolutionServices;

    public PluginDependencyResolutionServices(Factory<DependencyResolutionServices> factory) {
        this.factory = factory;
    }

    private DependencyResolutionServices getDependencyResolutionServices() {
        if (dependencyResolutionServices == null) {
            dependencyResolutionServices = factory.create();
        }
        return dependencyResolutionServices;
    }

    private RepositoryHandlerInternal getResolveRepositoryHandler() {
        return (RepositoryHandlerInternal) getDependencyResolutionServices().getResolveRepositoryHandler();
    }

    @Override
    public RepositoryHandler getPluginRepositoryHandler() {
        return getResolveRepositoryHandler();
    }

    @Override
    public PluginArtifactRepositories createPluginResolveRepositories() {
        return new DefaultPluginArtifactRepositories(factory, getResolveRepositoryHandler());
    }
}
