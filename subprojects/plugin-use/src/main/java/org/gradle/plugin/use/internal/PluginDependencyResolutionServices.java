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
import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.ServiceScope;
import org.gradle.plugin.use.resolve.internal.PluginArtifactRepositories;
import org.gradle.plugin.use.resolve.internal.PluginArtifactRepositoriesProvider;

@ServiceScope(Scopes.Build.class)
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
    public ConfigurationContainer getConfigurationContainer() {
        return getDependencyResolutionServices().getConfigurationContainer();
    }

    @Override
    public DependencyHandler getDependencyHandler() {
        return getDependencyResolutionServices().getDependencyHandler();
    }

    @Override
    public DependencyLockingHandler getDependencyLockingHandler() {
        return getDependencyResolutionServices().getDependencyLockingHandler();
    }

    @Override
    public ImmutableAttributesFactory getAttributesFactory() {
        return getDependencyResolutionServices().getAttributesFactory();
    }

    @Override
    public AttributesSchema getAttributesSchema() {
        return getDependencyResolutionServices().getAttributesSchema();
    }

    public PluginRepositoryHandlerProvider getPluginRepositoryHandlerProvider() {
        return this::getResolveRepositoryHandler;
    }

    @Override
    public ObjectFactory getObjectFactory() {
        return getDependencyResolutionServices().getObjectFactory();
    }

    public PluginRepositoriesProvider getPluginRepositoriesProvider() {
        return new DefaultPluginRepositoriesProvider();
    }

    private class DefaultPluginRepositoriesProvider implements PluginRepositoriesProvider {
        private final Object lock = new Object();
        private List<ArtifactRepository> repositories;

        @Override
        public void prepareForPluginResolution() {
            synchronized (lock) {
                if (repositories == null) {
                    RepositoryHandler pluginRepositories = getResolveRepositoryHandler();
                    if (pluginRepositories.isEmpty()) {
                        pluginRepositories.gradlePluginPortal();
                    }
                    repositories = pluginRepositories.stream().map(PluginArtifactRepository::new).collect(Collectors.toList());
                    pluginRepositories.whenObjectAdded(artifactRepository -> {
                        synchronized (lock) {
                            repositories = null;
                        }
                    });
                }
            }
        }

        @Override
        public List<ArtifactRepository> getPluginRepositories() {
            synchronized (lock) {
                if (repositories == null) {
                    throw new IllegalStateException("Plugin repositories have not been prepared.");
                }
                return repositories;
            }
        }

        @Override
        public boolean isExclusiveContentInUse() {
            return ((RepositoryHandlerInternal) getResolveRepositoryHandler()).isExclusiveContentInUse();
        }
    }


    private static class PluginArtifactRepository implements ArtifactRepositoryInternal, ContentFilteringRepository, ResolutionAwareRepository {
        private final ArtifactRepositoryInternal delegate;
        private final ResolutionAwareRepository resolutionAwareDelegate;
        private final RepositoryContentDescriptorInternal repositoryContentDescriptor;

        private PluginArtifactRepository(ArtifactRepository delegate) {
            this.delegate = (ArtifactRepositoryInternal) delegate;
            this.resolutionAwareDelegate = (ResolutionAwareRepository) delegate;
            this.repositoryContentDescriptor = this.delegate.getRepositoryDescriptorCopy();
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
