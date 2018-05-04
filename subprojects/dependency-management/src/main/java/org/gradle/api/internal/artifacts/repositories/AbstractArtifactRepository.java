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
import org.gradle.api.artifacts.ComponentMetadataSupplier;
import org.gradle.api.artifacts.ComponentMetadataVersionLister;
import org.gradle.api.artifacts.repositories.ArtifactRepository;
import org.gradle.api.artifacts.repositories.MetadataSupplierAware;
import org.gradle.api.artifacts.repositories.RepositoryResourceAccessor;
import org.gradle.api.internal.DefaultActionConfiguration;
import org.gradle.api.internal.InstantiatorFactory;
import org.gradle.api.internal.artifacts.repositories.resolver.ExternalRepositoryResourceAccessor;
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransport;
import org.gradle.internal.Factory;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.resource.local.FileStore;
import org.gradle.internal.service.DefaultServiceRegistry;

import java.net.URI;

public abstract class AbstractArtifactRepository implements ArtifactRepositoryInternal, MetadataSupplierAware {
    private static final Object[] NO_PARAMS = new Object[0];
    private final static Factory<ComponentMetadataSupplier> NO_METADATA_SUPPLIER = new Factory<ComponentMetadataSupplier>() {
        @Override
        public ComponentMetadataSupplier create() {
            return null;
        }
    };
    private final static Factory<ComponentMetadataVersionLister> NO_LISTER = new Factory<ComponentMetadataVersionLister>() {
        @Override
        public ComponentMetadataVersionLister create() {
            return null;
        }
    };

    private String name;
    private boolean isPartOfContainer;
    private Class<? extends ComponentMetadataSupplier> componentMetadataSupplierClass;
    private Object[] componentMetadataSupplierParams;
    private Class<? extends ComponentMetadataVersionLister> componentMetadataListerClass;
    private Object[] componentMetadataListerParams;

    public void onAddToContainer(NamedDomainObjectCollection<ArtifactRepository> container) {
        isPartOfContainer = true;
    }

    public String getName() {
        return name;
    }

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

    public void setMetadataSupplier(Class<? extends ComponentMetadataSupplier> ruleClass) {
        this.componentMetadataSupplierClass = ruleClass;
        this.componentMetadataSupplierParams = NO_PARAMS;
    }

    @Override
    public void setMetadataSupplier(Class<? extends ComponentMetadataSupplier> rule, Action<? super ActionConfiguration> configureAction) {
        DefaultActionConfiguration configuration = new DefaultActionConfiguration();
        configureAction.execute(configuration);
        this.componentMetadataSupplierClass = rule;
        this.componentMetadataSupplierParams = configuration.getParams();
    }

    @Override
    public void setComponentVersionsLister(Class<? extends ComponentMetadataVersionLister> lister) {
        this.componentMetadataListerClass = lister;
        this.componentMetadataListerParams = NO_PARAMS;
    }

    @Override
    public void setComponentVersionsLister(Class<? extends ComponentMetadataVersionLister> lister, Action<? super ActionConfiguration> configureAction) {
        DefaultActionConfiguration configuration = new DefaultActionConfiguration();
        configureAction.execute(configuration);
        this.componentMetadataListerClass = lister;
        this.componentMetadataListerParams = configuration.getParams();
    }

    Factory<ComponentMetadataSupplier> createComponentMetadataSupplierFactory(Instantiator instantiator) {
        return createFactory(instantiator, componentMetadataSupplierClass, componentMetadataSupplierParams, NO_METADATA_SUPPLIER);
    }

    Factory<ComponentMetadataVersionLister> createComponentMetadataVersionLister(final Instantiator instantiator) {
        return createFactory(instantiator, componentMetadataListerClass, componentMetadataListerParams, NO_LISTER);
    }

    /**
     * Creates a service registry giving access to the services we want to expose to rules and returns an instantiator that uses this service registry.
     *
     * @param transport the transport used to create the repository accessor
     * @param rootUri
     * @param externalResourcesFileStore
     * @return a dependency injecting instantiator, aware of services we want to expose
     */
    Instantiator createInjectorForMetadataSuppliers(final RepositoryTransport transport, InstantiatorFactory instantiatorFactory, final URI rootUri, final FileStore<String> externalResourcesFileStore) {
        DefaultServiceRegistry registry = new DefaultServiceRegistry();
        registry.addProvider(new Object() {
            RepositoryResourceAccessor createResourceAccessor() {
                return createRepositoryAccessor(transport, rootUri, externalResourcesFileStore);
            }
        });
        return instantiatorFactory.inject(registry);
    }

    private RepositoryResourceAccessor createRepositoryAccessor(RepositoryTransport transport, URI rootUri, FileStore<String> externalResourcesFileStore) {
        return new ExternalRepositoryResourceAccessor(rootUri, transport.getResourceAccessor(), externalResourcesFileStore);
    }


    private static <T> Factory<T> createFactory(final Instantiator instantiator, final Class<? extends T> clazz, final Object[] params, Factory<T> fallback) {
        if (clazz == null) {
            return fallback;
        }

        return new Factory<T>() {
            @Override
            public T create() {
                return instantiator.newInstance(clazz, params);
            }
        };
    }

}
