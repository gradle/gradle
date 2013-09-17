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
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.internal.DomainObjectContext;
import org.gradle.api.internal.artifacts.configurations.ConfigurationContainerInternal;
import org.gradle.api.internal.artifacts.configurations.DefaultConfigurationContainer;
import org.gradle.api.internal.artifacts.configurations.DependencyMetaDataProvider;
import org.gradle.api.internal.artifacts.dsl.DefaultArtifactHandler;
import org.gradle.api.internal.artifacts.dsl.DefaultComponentMetadataHandler;
import org.gradle.api.internal.artifacts.dsl.DefaultRepositoryHandler;
import org.gradle.api.internal.artifacts.dsl.PublishArtifactNotationParserFactory;
import org.gradle.api.internal.artifacts.dsl.dependencies.DefaultDependencyHandler;
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyFactory;
import org.gradle.api.internal.artifacts.dsl.dependencies.ProjectFinder;
import org.gradle.api.internal.artifacts.ivyservice.*;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.LatestStrategy;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.ResolverStrategy;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionMatcher;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.PublishModuleDescriptorConverter;
import org.gradle.api.internal.artifacts.mvnsettings.LocalMavenRepositoryLocator;
import org.gradle.api.internal.artifacts.repositories.DefaultBaseRepositoryFactory;
import org.gradle.api.internal.artifacts.repositories.legacy.LegacyDependencyResolverRepositoryFactory;
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransportFactory;
import org.gradle.api.internal.externalresource.local.LocallyAvailableResourceFinder;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.notations.api.NotationParser;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.listener.ListenerManager;

public class DefaultDependencyManagementServices implements DependencyManagementServices {

    private final ServiceRegistry parent;

    public DefaultDependencyManagementServices(ServiceRegistry parent) {
        this.parent = parent;
    }

    public DependencyResolutionServices create(FileResolver resolver, DependencyMetaDataProvider dependencyMetaDataProvider, ProjectFinder projectFinder, DomainObjectContext domainObjectContext) {
        return new DefaultDependencyResolutionServices(parent, resolver, dependencyMetaDataProvider, projectFinder, domainObjectContext);
    }

    private <T> T get(Class<T> type) {
        return parent.get(type);
    }

    private class DefaultDependencyResolutionServices implements DependencyResolutionServices {
        private final ServiceRegistry parent;
        private final FileResolver fileResolver;
        private final DependencyMetaDataProvider dependencyMetaDataProvider;
        private final ProjectFinder projectFinder;
        private final DomainObjectContext domainObjectContext;
        private DefaultRepositoryHandler repositoryHandler;
        private ConfigurationContainerInternal configurationContainer;
        private DependencyHandler dependencyHandler;
        private DefaultComponentMetadataHandler componentMetadataHandler;
        private DefaultArtifactHandler artifactHandler;
        private BaseRepositoryFactory baseRepositoryFactory;

        private DefaultDependencyResolutionServices(ServiceRegistry parent, FileResolver fileResolver, DependencyMetaDataProvider dependencyMetaDataProvider, ProjectFinder projectFinder, DomainObjectContext domainObjectContext) {
            this.parent = parent;
            this.fileResolver = fileResolver;
            this.dependencyMetaDataProvider = dependencyMetaDataProvider;
            this.projectFinder = projectFinder;
            this.domainObjectContext = domainObjectContext;
        }

        public DefaultRepositoryHandler getResolveRepositoryHandler() {
            if (repositoryHandler == null) {
                repositoryHandler = createRepositoryHandler();
            }
            return repositoryHandler;
        }

        public BaseRepositoryFactory getBaseRepositoryFactory() {
            if (baseRepositoryFactory == null) {
                Instantiator instantiator = parent.get(Instantiator.class);
                //noinspection unchecked
                baseRepositoryFactory = new DefaultBaseRepositoryFactory(
                        get(LocalMavenRepositoryLocator.class),
                        fileResolver,
                        instantiator,
                        get(RepositoryTransportFactory.class),
                        get(LocallyAvailableResourceFinder.class),
                        getComponentMetadataHandler(),
                        get(LegacyDependencyResolverRepositoryFactory.class),
                        get(VersionMatcher.class),
                        get(LatestStrategy.class),
                        get(ResolverStrategy.class)
                );
            }

            return baseRepositoryFactory;
        }

        private DefaultRepositoryHandler createRepositoryHandler() {
            Instantiator instantiator = parent.get(Instantiator.class);
            return instantiator.newInstance(DefaultRepositoryHandler.class, getBaseRepositoryFactory(), instantiator);
        }

        public ConfigurationContainerInternal getConfigurationContainer() {
            if (configurationContainer == null) {
                final Instantiator instantiator = parent.get(Instantiator.class);
                ConfigurationResolver resolver = createDependencyResolver(getResolveRepositoryHandler());
                configurationContainer = instantiator.newInstance(DefaultConfigurationContainer.class,
                        resolver, instantiator, domainObjectContext, parent.get(ListenerManager.class),
                        dependencyMetaDataProvider);
            }
            return configurationContainer;
        }

        public DependencyHandler getDependencyHandler() {
            if (dependencyHandler == null) {
                dependencyHandler = new DefaultDependencyHandler(getConfigurationContainer(), parent.get(DependencyFactory.class), projectFinder, getComponentMetadataHandler());
            }
            return dependencyHandler;
        }

        public DefaultComponentMetadataHandler getComponentMetadataHandler() {
            if (componentMetadataHandler == null) {
                Instantiator instantiator = parent.get(Instantiator.class);
                componentMetadataHandler = instantiator.newInstance(DefaultComponentMetadataHandler.class, instantiator);
            }
            return componentMetadataHandler;
        }

        public ArtifactHandler getArtifactHandler() {
            if (artifactHandler == null) {
                NotationParser<PublishArtifact> publishArtifactNotationParser = new PublishArtifactNotationParserFactory(get(Instantiator.class), dependencyMetaDataProvider).create();
                artifactHandler = new DefaultArtifactHandler(getConfigurationContainer(), publishArtifactNotationParser);
            }
            return artifactHandler;
        }

        public ArtifactPublicationServices createArtifactPublicationServices() {
                return new DefaultArtifactPublicationServices(DefaultDependencyResolutionServices.this);
        }

        ConfigurationResolver createDependencyResolver(DefaultRepositoryHandler repositories) {
            return new DefaultConfigurationResolver(
                    get(ArtifactDependencyResolver.class),
                    repositories);
        }

        ArtifactPublisher createArtifactPublisher() {
            return new IvyBackedArtifactPublisher(
                    get(PublishModuleDescriptorConverter.class),
                    get(IvyContextManager.class),
                    new DefaultIvyDependencyPublisher(),
                    new IvyXmlModuleDescriptorWriter()
            );
        }
    }

    private static class DefaultArtifactPublicationServices implements ArtifactPublicationServices {
        private final DefaultDependencyResolutionServices dependencyResolutionServices;

        public DefaultArtifactPublicationServices(DefaultDependencyResolutionServices dependencyResolutionServices) {
            this.dependencyResolutionServices = dependencyResolutionServices;
        }

        public DefaultRepositoryHandler createRepositoryHandler() {
            return dependencyResolutionServices.createRepositoryHandler();
        }

        public ArtifactPublisher createArtifactPublisher() {
            return dependencyResolutionServices.createArtifactPublisher();
        }
    }

}
