/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.artifacts;

import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.artifacts.dsl.ArtifactHandler;
import org.gradle.api.artifacts.dsl.ComponentMetadataHandler;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.internal.DomainObjectContext;
import org.gradle.api.internal.artifacts.configurations.ConfigurationContainerInternal;
import org.gradle.api.internal.artifacts.configurations.DefaultConfigurationContainer;
import org.gradle.api.internal.artifacts.configurations.DependencyMetaDataProvider;
import org.gradle.api.internal.artifacts.dsl.DefaultArtifactHandler;
import org.gradle.api.internal.artifacts.dsl.DefaultComponentMetadataHandler;
import org.gradle.api.internal.artifacts.dsl.DefaultRepositoryHandler;
import org.gradle.api.internal.artifacts.dsl.PublishArtifactNotationParserFactory;
import org.gradle.api.internal.artifacts.dsl.dependencies.ArtifactResolutionQueryFactory;
import org.gradle.api.internal.artifacts.dsl.dependencies.DefaultDependencyHandler;
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyFactory;
import org.gradle.api.internal.artifacts.dsl.dependencies.ProjectFinder;
import org.gradle.api.internal.artifacts.ivyservice.*;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ResolveIvyFactory;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.ResolverStrategy;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.PublishLocalComponentFactory;
import org.gradle.api.internal.artifacts.metadata.ModuleVersionArtifactMetaData;
import org.gradle.api.internal.artifacts.mvnsettings.LocalMavenRepositoryLocator;
import org.gradle.api.internal.artifacts.repositories.DefaultBaseRepositoryFactory;
import org.gradle.api.internal.artifacts.repositories.legacy.LegacyDependencyResolverRepositoryFactory;
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransportFactory;
import org.gradle.api.internal.artifacts.resolution.DefaultArtifactResolutionQueryFactory;
import org.gradle.api.internal.externalresource.local.LocallyAvailableResourceFinder;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.service.DefaultServiceRegistry;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.typeconversion.NotationParser;
import org.gradle.listener.ListenerManager;

public class DefaultDependencyManagementServices implements DependencyManagementServices {

    private final ServiceRegistry parent;

    public DefaultDependencyManagementServices(ServiceRegistry parent) {
        this.parent = parent;
    }

    public DependencyResolutionServices create(FileResolver fileResolver, DependencyMetaDataProvider dependencyMetaDataProvider, ProjectFinder projectFinder, DomainObjectContext domainObjectContext) {
        DefaultServiceRegistry services = new DefaultServiceRegistry(parent);
        services.add(FileResolver.class, fileResolver);
        services.add(DependencyMetaDataProvider.class, dependencyMetaDataProvider);
        services.add(ProjectFinder.class, projectFinder);
        services.add(DomainObjectContext.class, domainObjectContext);
        services.addProvider(new DependencyResolutionScopeServices());
        return services.get(DependencyResolutionServices.class);
    }

    public void addDslServices(ServiceRegistration registration) {
        registration.addProvider(new DependencyResolutionScopeServices());
    }

    private static class DependencyResolutionScopeServices {
        BaseRepositoryFactory createBaseRepositoryFactory(LocalMavenRepositoryLocator localMavenRepositoryLocator, Instantiator instantiator, FileResolver fileResolver,
                                                          RepositoryTransportFactory repositoryTransportFactory, LocallyAvailableResourceFinder<ModuleVersionArtifactMetaData> locallyAvailableResourceFinder,
                                                          LegacyDependencyResolverRepositoryFactory legacyDependencyResolverRepositoryFactory,
                                                          ResolverStrategy resolverStrategy) {
            return new DefaultBaseRepositoryFactory(
                    localMavenRepositoryLocator,
                    fileResolver,
                    instantiator,
                    repositoryTransportFactory,
                    locallyAvailableResourceFinder,
                    legacyDependencyResolverRepositoryFactory,
                    resolverStrategy
            );
        }

        RepositoryHandler createRepositoryHandler(Instantiator instantiator, BaseRepositoryFactory baseRepositoryFactory) {
            return instantiator.newInstance(DefaultRepositoryHandler.class, baseRepositoryFactory, instantiator);
        }

        ConfigurationContainerInternal createConfigurationContainer(Instantiator instantiator, ConfigurationResolver configurationResolver, DomainObjectContext domainObjectContext,
                                                                    ListenerManager listenerManager, DependencyMetaDataProvider metaDataProvider) {
            return instantiator.newInstance(DefaultConfigurationContainer.class,
                    configurationResolver,
                    instantiator,
                    domainObjectContext,
                    listenerManager,
                    metaDataProvider);
        }

        DependencyHandler createDependencyHandler(Instantiator instantiator, ConfigurationContainerInternal configurationContainer, DependencyFactory dependencyFactory,
                                                  ProjectFinder projectFinder, ComponentMetadataHandler componentMetadataHandler, ArtifactResolutionQueryFactory resolutionQueryFactory) {
            return instantiator.newInstance(DefaultDependencyHandler.class,
                    configurationContainer,
                    dependencyFactory,
                    projectFinder,
                    componentMetadataHandler,
                    resolutionQueryFactory);
        }

        DefaultComponentMetadataHandler createComponentMetadataHandler(Instantiator instantiator) {
            return instantiator.newInstance(DefaultComponentMetadataHandler.class, instantiator);
        }

        ArtifactHandler createArtifactHandler(Instantiator instantiator, DependencyMetaDataProvider dependencyMetaDataProvider, ConfigurationContainerInternal configurationContainer) {
            NotationParser<Object, PublishArtifact> publishArtifactNotationParser = new PublishArtifactNotationParserFactory(instantiator, dependencyMetaDataProvider).create();
            return new DefaultArtifactHandler(configurationContainer, publishArtifactNotationParser);
        }

        ConfigurationResolver createDependencyResolver(ArtifactDependencyResolver artifactDependencyResolver, RepositoryHandler repositories,
                                                       ModuleMetadataProcessor metadataProcessor) {
            return new DefaultConfigurationResolver(artifactDependencyResolver, repositories, metadataProcessor);
        }

        ArtifactPublicationServices createArtifactPublicationServices(ServiceRegistry services) {
            return new DefaultArtifactPublicationServices(services);
        }

        DependencyResolutionServices createDependencyResolutionServices(ServiceRegistry services) {
            return new DefaultDependencyResolutionServices(services);
        }

        ArtifactResolutionQueryFactory createArtifactResolutionQueryFactory(ConfigurationContainerInternal configurationContainer, RepositoryHandler repositoryHandler,
                                                                            ResolveIvyFactory ivyFactory, ModuleMetadataProcessor metadataProcessor,
                                                                            CacheLockingManager cacheLockingManager) {
            return new DefaultArtifactResolutionQueryFactory(configurationContainer, repositoryHandler, ivyFactory, metadataProcessor, cacheLockingManager);

        }
    }

    private static class DefaultDependencyResolutionServices implements DependencyResolutionServices {
        private final ServiceRegistry services;

        private DefaultDependencyResolutionServices(ServiceRegistry services) {
            this.services = services;
        }

        public RepositoryHandler getResolveRepositoryHandler() {
            return services.get(RepositoryHandler.class);
        }

        public ConfigurationContainerInternal getConfigurationContainer() {
            return services.get(ConfigurationContainerInternal.class);
        }

        public DependencyHandler getDependencyHandler() {
            return services.get(DependencyHandler.class);
        }
    }

    private static class DefaultArtifactPublicationServices implements ArtifactPublicationServices {
        private final ServiceRegistry services;

        public DefaultArtifactPublicationServices(ServiceRegistry services) {
            this.services = services;
        }

        public RepositoryHandler createRepositoryHandler() {
            Instantiator instantiator = services.get(Instantiator.class);
            BaseRepositoryFactory baseRepositoryFactory = services.get(BaseRepositoryFactory.class);
            return instantiator.newInstance(DefaultRepositoryHandler.class, baseRepositoryFactory, instantiator);
        }

        public ArtifactPublisher createArtifactPublisher() {
            return new IvyBackedArtifactPublisher(
                    services.get(PublishLocalComponentFactory.class),
                    services.get(IvyContextManager.class),
                    new DefaultIvyDependencyPublisher(),
                    new IvyXmlModuleDescriptorWriter()
            );
        }
    }
}
