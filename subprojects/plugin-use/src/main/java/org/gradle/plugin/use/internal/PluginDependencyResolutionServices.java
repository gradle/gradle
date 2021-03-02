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

import org.gradle.api.Action;
import org.gradle.api.NamedDomainObjectCollection;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.artifacts.dsl.DependencyLockingHandler;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.artifacts.repositories.ArtifactRepository;
import org.gradle.api.artifacts.repositories.RepositoryContentDescriptor;
import org.gradle.api.attributes.AttributesSchema;
import org.gradle.api.internal.artifacts.DependencyResolutionServices;
import org.gradle.api.internal.artifacts.dsl.RepositoryHandlerInternal;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ConfiguredModuleComponentRepository;
import org.gradle.api.internal.artifacts.repositories.ArtifactRepositoryInternal;
import org.gradle.api.internal.artifacts.repositories.ArtifactResolutionDetails;
import org.gradle.api.internal.artifacts.repositories.ContentFilteringRepository;
import org.gradle.api.internal.artifacts.repositories.RepositoryContentDescriptorInternal;
import org.gradle.api.internal.artifacts.repositories.ResolutionAwareRepository;
import org.gradle.api.internal.artifacts.repositories.descriptor.RepositoryDescriptor;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.api.model.ObjectFactory;
import org.gradle.internal.Factory;

import java.util.List;
import java.util.stream.Collectors;

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

        @Override
        public List<ArtifactRepository> getPluginRepositories() {
            return getResolveRepositoryHandler().stream().map(PluginArtifactRepository::new).collect(Collectors.toList());
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
        public String getName() {
            return REPOSITORY_NAME_PREFIX + delegate.getName();
        }

        @Override
        public void setName(String name) {
            delegate.setName(name);
        }

        @Override
        public void content(Action<? super RepositoryContentDescriptor> configureAction) {
            configureAction.execute(repositoryContentDescriptor);
        }

        @Override
        public Action<? super ArtifactResolutionDetails> getContentFilter() {
            return repositoryContentDescriptor.toContentFilter();
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
        public RepositoryDescriptor getDescriptor() {
            return resolutionAwareDelegate.getDescriptor();
        }

        @Override
        public void onAddToContainer(NamedDomainObjectCollection<ArtifactRepository> container) {
            delegate.onAddToContainer(container);
        }

        @Override
        public RepositoryContentDescriptorInternal createRepositoryDescriptor() {
            return delegate.createRepositoryDescriptor();
        }

        @Override
        public RepositoryContentDescriptorInternal getRepositoryDescriptorCopy() {
            return repositoryContentDescriptor.asMutableCopy();
        }
    }
}
