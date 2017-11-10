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

import org.gradle.api.NamedDomainObjectCollection;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.artifacts.repositories.ArtifactRepository;
import org.gradle.api.internal.artifacts.DependencyResolutionServices;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ConfiguredModuleComponentRepository;
import org.gradle.api.internal.artifacts.repositories.ArtifactRepositoryInternal;
import org.gradle.api.internal.artifacts.repositories.ResolutionAwareRepository;
import org.gradle.internal.Factory;

import java.util.ArrayList;
import java.util.List;

public class PluginDependencyResolutionServices implements DependencyResolutionServices {

    private static final String REPOSITORY_NAME_PREFIX = "__plugin_repository__";

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

    @Override
    public RepositoryHandler getResolveRepositoryHandler() {
        return getDependencyResolutionServices().getResolveRepositoryHandler();
    }

    @Override
    public ConfigurationContainer getConfigurationContainer() {
        return getDependencyResolutionServices().getConfigurationContainer();
    }

    @Override
    public DependencyHandler getDependencyHandler() {
        return getDependencyResolutionServices().getDependencyHandler();
    }


    public PluginRepositoryHandlerProvider getPluginRepositoryHandlerProvider() {
        return new PluginRepositoryHandlerProvider() {
            @Override
            public RepositoryHandler getPluginRepositoryHandler() {
                return getResolveRepositoryHandler();
            }
        };
    }

    public PluginRepositoriesProvider getPluginRepositoriesProvider() {
        return new PluginRepositoriesProvider() {
            @Override
            public List<ArtifactRepository> getPluginRepositories() {
                RepositoryHandler repositories = getResolveRepositoryHandler();
                List<ArtifactRepository> list = new ArrayList<ArtifactRepository>(repositories.size());
                for (ArtifactRepository repository : repositories) {
                    list.add(new PluginArtifactRepository(repository));
                }
                return list;
            }
        };
    }

    private static class PluginArtifactRepository implements ArtifactRepositoryInternal, ResolutionAwareRepository {
        private final ArtifactRepositoryInternal delegate;
        private final ResolutionAwareRepository resolutionAwareDelegate;

        private PluginArtifactRepository(ArtifactRepository delegate) {
            this.delegate = (ArtifactRepositoryInternal) delegate;
            this.resolutionAwareDelegate = (ResolutionAwareRepository) delegate;
        }

        @Override
        public String getName() {
            return REPOSITORY_NAME_PREFIX + delegate.getName();
        }

        @Override
        public void setName(String name) {
            delegate.setName(name);
        }

        @Override
        public String getDisplayName() {
            return delegate.getDisplayName();
        }

        @Override
        public ConfiguredModuleComponentRepository createResolver() {
            return resolutionAwareDelegate.createResolver();
        }

        @Override
        public void onAddToContainer(NamedDomainObjectCollection<ArtifactRepository> container) {
            delegate.onAddToContainer(container);
        }
    }
}
