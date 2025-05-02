/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.internal.artifacts.repositories;

import org.gradle.api.Action;
import org.gradle.api.ActionConfiguration;
import org.gradle.api.NamedDomainObjectCollection;
import org.gradle.api.artifacts.ComponentMetadataListerDetails;
import org.gradle.api.artifacts.ComponentMetadataSupplier;
import org.gradle.api.artifacts.ComponentMetadataSupplierDetails;
import org.gradle.api.artifacts.ComponentMetadataVersionLister;
import org.gradle.api.artifacts.repositories.ArtifactRepository;
import org.gradle.api.artifacts.repositories.MetadataSupplierAware;
import org.gradle.api.artifacts.repositories.RepositoryContentDescriptor;
import org.gradle.api.artifacts.repositories.RepositoryResourceAccessor;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionParser;
import org.gradle.api.internal.artifacts.repositories.resolver.ExternalRepositoryResourceAccessor;
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransport;
import org.gradle.api.model.ObjectFactory;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.action.ConfigurableRule;
import org.gradle.internal.action.DefaultConfigurableRule;
import org.gradle.internal.action.DefaultConfigurableRules;
import org.gradle.internal.action.InstantiatingAction;
import org.gradle.internal.instantiation.InstantiatorFactory;
import org.gradle.internal.isolation.IsolatableFactory;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.resolve.caching.ImplicitInputsCapturingInstantiator;
import org.gradle.internal.resource.local.FileStore;
import org.gradle.internal.service.Provides;
import org.gradle.internal.service.ServiceRegistrationProvider;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.service.ServiceRegistryBuilder;
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.util.Map;
import java.util.Set;

public abstract class AbstractArtifactRepository implements ArtifactRepositoryInternal, ContentFilteringRepository, MetadataSupplierAware {
    private String name;
    private boolean isPartOfContainer;
    private Class<? extends ComponentMetadataSupplier> componentMetadataSupplierRuleClass;
    private Class<? extends ComponentMetadataVersionLister> componentMetadataListerRuleClass;
    private Action<? super ActionConfiguration> componentMetadataSupplierRuleConfiguration;
    private Action<? super ActionConfiguration> componentMetadataListerRuleConfiguration;
    private final ObjectFactory objectFactory;
    private final RepositoryContentDescriptorInternal repositoryContentDescriptor;

    protected AbstractArtifactRepository(ObjectFactory objectFactory, VersionParser versionParser) {
        this.objectFactory = objectFactory;
        this.repositoryContentDescriptor = createRepositoryDescriptor(versionParser);
    }

    @Override
    public void onAddToContainer(NamedDomainObjectCollection<ArtifactRepository> container) {
        isPartOfContainer = true;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void setName(String name) {
        if (isPartOfContainer) {
            throw new IllegalStateException("The name of an ArtifactRepository cannot be changed after it has been added to a repository container. You should set the name when creating the repository.");
        }
        this.name = name;
    }

    @Override
    public String getDisplayName() {
        return getName();
    }

    @Override
    public void setMetadataSupplier(Class<? extends ComponentMetadataSupplier> ruleClass) {
        this.componentMetadataSupplierRuleClass = ruleClass;
        this.componentMetadataSupplierRuleConfiguration = null;
    }

    @Override
    public void setMetadataSupplier(Class<? extends ComponentMetadataSupplier> rule, Action<? super ActionConfiguration> configureAction) {
        this.componentMetadataSupplierRuleClass = rule;
        this.componentMetadataSupplierRuleConfiguration = configureAction;
    }

    @Override
    public void setComponentVersionsLister(Class<? extends ComponentMetadataVersionLister> lister) {
        this.componentMetadataListerRuleClass = lister;
        this.componentMetadataListerRuleConfiguration = null;
    }

    @Override
    public void setComponentVersionsLister(Class<? extends ComponentMetadataVersionLister> lister, Action<? super ActionConfiguration> configureAction) {
        this.componentMetadataListerRuleClass = lister;
        this.componentMetadataListerRuleConfiguration = configureAction;
    }

    @Override
    public RepositoryContentDescriptorInternal createRepositoryDescriptor(VersionParser versionParser) {
        return new DefaultRepositoryContentDescriptor(this::getDisplayName, versionParser);
    }

    @Override
    public RepositoryContentDescriptorInternal getRepositoryDescriptorCopy() {
        return repositoryContentDescriptor.asMutableCopy();
    }

    @Override
    public Action<? super ArtifactResolutionDetails> getContentFilter() {
        return repositoryContentDescriptor.toContentFilter();
    }

    @Override
    public Set<String> getIncludedConfigurations() {
        return repositoryContentDescriptor.getIncludedConfigurations();
    }

    @Override
    public Set<String> getExcludedConfigurations() {
        return repositoryContentDescriptor.getExcludedConfigurations();
    }

    @Override
    public Map<Attribute<Object>, Set<Object>> getRequiredAttributes() {
        return repositoryContentDescriptor.getRequiredAttributes();
    }

    @Override
    public void content(Action<? super RepositoryContentDescriptor> configureAction) {
        configureAction.execute(repositoryContentDescriptor);
    }

    @Nullable
    InstantiatingAction<ComponentMetadataSupplierDetails> createComponentMetadataSupplierFactory(Instantiator instantiator, IsolatableFactory isolatableFactory) {
        if (componentMetadataSupplierRuleClass != null) {
            return createRuleAction(instantiator, DefaultConfigurableRule.of(componentMetadataSupplierRuleClass, componentMetadataSupplierRuleConfiguration, isolatableFactory));
        } else {
            return null;
        }
    }

    @Nullable
    InstantiatingAction<ComponentMetadataListerDetails> createComponentMetadataVersionLister(Instantiator instantiator, IsolatableFactory isolatableFactory) {
        if (componentMetadataListerRuleClass != null) {
            return createRuleAction(instantiator, DefaultConfigurableRule.of(componentMetadataListerRuleClass, componentMetadataListerRuleConfiguration, isolatableFactory));
        } else {
            return null;
        }
    }

    /**
     * Creates a service registry giving access to the services we want to expose to rules and returns an instantiator that uses this service registry.
     *
     * @param transport the transport used to create the repository accessor
     * @return a dependency injecting instantiator, aware of services we want to expose
     */
    ImplicitInputsCapturingInstantiator createInjectorForMetadataSuppliers(final RepositoryTransport transport, InstantiatorFactory instantiatorFactory, final URI rootUri, final FileStore<String> externalResourcesFileStore) {
        ServiceRegistry serviceRegistry = ServiceRegistryBuilder.builder()
            .displayName("implicit inputs capturing instantiator services")
            .provider(new ServiceRegistrationProvider() {
                @Provides
                RepositoryResourceAccessor createResourceAccessor() {
                    return createRepositoryAccessor(transport, rootUri, externalResourcesFileStore);
                }
            })
            .provider(registration -> {
                registration.add(ObjectFactory.class, objectFactory);
            })
            .build();
        return new ImplicitInputsCapturingInstantiator(serviceRegistry, instantiatorFactory);
    }

    protected RepositoryResourceAccessor createRepositoryAccessor(RepositoryTransport transport, URI rootUri, FileStore<String> externalResourcesFileStore) {
        return new ExternalRepositoryResourceAccessor(rootUri, transport.getResourceAccessor(), externalResourcesFileStore);
    }


    private static <T> InstantiatingAction<T> createRuleAction(final Instantiator instantiator, final ConfigurableRule<T> rule) {
        return new InstantiatingAction<>(DefaultConfigurableRules.of(rule), instantiator, (target, throwable) -> {
            throw UncheckedException.throwAsUncheckedException(throwable);
        });
    }
}
