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

import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.gradle.StartParameter;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.internal.ClassPathRegistry;
import org.gradle.api.internal.DomainObjectContext;
import org.gradle.api.internal.Factory;
import org.gradle.api.internal.Instantiator;
import org.gradle.api.internal.artifacts.configurations.ConfigurationContainerInternal;
import org.gradle.api.internal.artifacts.configurations.DefaultConfigurationContainer;
import org.gradle.api.internal.artifacts.configurations.DependencyMetaDataProvider;
import org.gradle.api.internal.artifacts.dsl.DefaultRepositoryHandler;
import org.gradle.api.internal.artifacts.dsl.dependencies.*;
import org.gradle.api.internal.artifacts.ivyservice.*;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.*;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.*;
import org.gradle.api.internal.artifacts.mvnsettings.DefaultLocalMavenCacheLocator;
import org.gradle.api.internal.artifacts.repositories.DefaultInternalRepository;
import org.gradle.api.internal.artifacts.repositories.DefaultResolverFactory;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.file.IdentityFileResolver;
import org.gradle.api.internal.project.DefaultServiceRegistry;
import org.gradle.api.internal.project.ServiceRegistry;
import org.gradle.cache.CacheRepository;
import org.gradle.cache.internal.FileLockManager;
import org.gradle.listener.ListenerManager;
import org.gradle.logging.ProgressLoggerFactory;
import org.gradle.util.WrapUtil;
import org.jfrog.wharf.ivy.lock.LockHolderFactory;

import java.util.HashMap;
import java.util.Map;

public class DefaultDependencyManagementServices extends DefaultServiceRegistry implements DependencyManagementServices {
    private final Map<String, ModuleDescriptor> clientModuleRegistry = new HashMap<String, ModuleDescriptor>();

    public DefaultDependencyManagementServices(ServiceRegistry parent) {
        super(parent);
    }

    public DependencyResolutionServices create(FileResolver resolver, DependencyMetaDataProvider dependencyMetaDataProvider, ProjectFinder projectFinder, DomainObjectContext domainObjectContext) {
        return new DefaultDependencyResolutionServices(this, resolver, dependencyMetaDataProvider, projectFinder, domainObjectContext);
    }

    protected PublishModuleDescriptorConverter createPublishModuleDescriptorConverter() {
        return new PublishModuleDescriptorConverter(
                createResolveModuleDescriptorConverter(ProjectDependencyDescriptorFactory.RESOLVE_DESCRIPTOR_STRATEGY),
                new DefaultArtifactsToModuleDescriptorConverter(DefaultArtifactsToModuleDescriptorConverter.RESOLVE_STRATEGY));
    }

    protected ModuleDescriptorFactory createModuleDescriptorFactory() {
        return new DefaultModuleDescriptorFactory();
    }

    protected ExcludeRuleConverter createExcludeRuleConverter() {
        return new DefaultExcludeRuleConverter();
    }

    protected ExternalModuleDependencyDescriptorFactory createExternalModuleDependencyDescriptorFactory() {
        return new ExternalModuleDependencyDescriptorFactory(get(ExcludeRuleConverter.class));
    }

    protected ConfigurationsToModuleDescriptorConverter createConfigurationsToModuleDescriptorConverter() {
        return new DefaultConfigurationsToModuleDescriptorConverter();
    }

    private ResolveModuleDescriptorConverter createResolveModuleDescriptorConverter(ProjectDependencyDescriptorStrategy projectDependencyStrategy) {
        DependencyDescriptorFactory dependencyDescriptorFactoryDelegate = createDependencyDescriptorFactory(projectDependencyStrategy);
        return new ResolveModuleDescriptorConverter(
                get(ModuleDescriptorFactory.class),
                get(ConfigurationsToModuleDescriptorConverter.class),
                new DefaultDependenciesToModuleDescriptorConverter(
                        dependencyDescriptorFactoryDelegate,
                        get(ExcludeRuleConverter.class)));
    }

    private DependencyDescriptorFactory createDependencyDescriptorFactory(ProjectDependencyDescriptorStrategy projectDependencyStrategy) {
        DefaultModuleDescriptorFactoryForClientModule clientModuleDescriptorFactory = new DefaultModuleDescriptorFactoryForClientModule();
        DependencyDescriptorFactory dependencyDescriptorFactoryDelegate = new DependencyDescriptorFactoryDelegate(
                new ClientModuleDependencyDescriptorFactory(
                        get(ExcludeRuleConverter.class), clientModuleDescriptorFactory, clientModuleRegistry),
                new ProjectDependencyDescriptorFactory(
                        get(ExcludeRuleConverter.class),
                        projectDependencyStrategy),
                get(ExternalModuleDependencyDescriptorFactory.class));
        clientModuleDescriptorFactory.setDependencyDescriptorFactory(dependencyDescriptorFactoryDelegate);
        return dependencyDescriptorFactoryDelegate;
    }

    protected DependencyFactory createDependencyFactory() {
        Instantiator instantiator = get(Instantiator.class);
        DefaultProjectDependencyFactory projectDependencyFactory = new DefaultProjectDependencyFactory(
                get(StartParameter.class).getProjectDependenciesBuildInstruction(),
                instantiator);
        return new DefaultDependencyFactory(
                WrapUtil.<IDependencyImplementationFactory>toSet(
                        new ModuleDependencyFactory(
                                instantiator),
                        new SelfResolvingDependencyFactory(
                                instantiator),
                        new ClassPathDependencyFactory(
                                instantiator,
                                get(ClassPathRegistry.class),
                                new IdentityFileResolver()),
                        projectDependencyFactory),
                new DefaultClientModuleFactory(
                        instantiator),
                projectDependencyFactory);
    }

    protected ArtifactCacheMetaData createArtifactCacheMetaData() {
        return new ArtifactCacheMetaData(get(CacheRepository.class));
    }

    protected DefaultCacheLockingManager createCacheLockingManager() {
        return new DefaultCacheLockingManager(
                get(FileLockManager.class)
        );
    }

    protected SettingsConverter createSettingsConverter() {
        return new DefaultSettingsConverter(
                get(ProgressLoggerFactory.class),
                new IvySettingsFactory(
                        get(ArtifactCacheMetaData.class),
                        get(LockHolderFactory.class)));
    }

    protected IvyFactory createIvyFactory() {
        return  new DefaultIvyFactory();
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

        private DefaultRepositoryHandler createRepositoryHandler() {
            Instantiator instantiator = parent.get(Instantiator.class);
            ResolverFactory resolverFactory = new DefaultResolverFactory(
                    new DefaultLocalMavenCacheLocator(),
                    fileResolver,
                    instantiator);
            return instantiator.newInstance(DefaultRepositoryHandler.class, resolverFactory, instantiator);
        }

        public ConfigurationContainerInternal getConfigurationContainer() {
            if (configurationContainer == null) {
                Instantiator instantiator = parent.get(Instantiator.class);
                ArtifactDependencyResolver dependencyResolver = createDependencyResolver(getResolveRepositoryHandler());
                configurationContainer = instantiator.newInstance(DefaultConfigurationContainer.class,
                        dependencyResolver, instantiator, domainObjectContext, parent.get(ListenerManager.class),
                        dependencyMetaDataProvider);
            }
            return configurationContainer;
        }

        public DependencyHandler getDependencyHandler() {
            if (dependencyHandler == null) {
                dependencyHandler = new DefaultDependencyHandler(getConfigurationContainer(), parent.get(DependencyFactory.class), projectFinder);
            }
            return dependencyHandler;
        }

        public Factory<ArtifactPublicationServices> getPublishServicesFactory() {
            return new Factory<ArtifactPublicationServices>() {
                public ArtifactPublicationServices create() {
                    return new DefaultArtifactPublicationServices(DefaultDependencyResolutionServices.this);
                }
            };
        }

        ArtifactDependencyResolver createDependencyResolver(DefaultRepositoryHandler resolverProvider) {
            return new ErrorHandlingArtifactDependencyResolver(
                    new EventBroadcastingArtifactDependencyResolver(
                            new ShortcircuitEmptyConfigsArtifactDependencyResolver(
                                    new SelfResolvingDependencyResolver(
                                            new CacheLockingArtifactDependencyResolver(
                                                    get(CacheLockingManager.class),
                                                    new DefaultIvyDependencyResolver(
                                                            new DefaultIvyReportConverter(
                                                                    createDependencyDescriptorFactory(ProjectDependencyDescriptorFactory.RESOLVE_DESCRIPTOR_STRATEGY),
                                                                    new ResolvedArtifactFactory(
                                                                            get(CacheLockingManager.class)
                                                                    )),
                                                            get(PublishModuleDescriptorConverter.class),
                                                            new ResolveIvyFactory(
                                                                    get(IvyFactory.class),
                                                                    resolverProvider,
                                                                    get(SettingsConverter.class),
                                                                    new DefaultInternalRepository(
                                                                            projectFinder,
                                                                            get(ModuleDescriptorConverter.class)),
                                                                    clientModuleRegistry)))))));
        }

        ArtifactPublisher createArtifactPublisher(DefaultRepositoryHandler resolverProvider) {
            PublishModuleDescriptorConverter fileModuleDescriptorConverter = new PublishModuleDescriptorConverter(
                    createResolveModuleDescriptorConverter(ProjectDependencyDescriptorFactory.IVY_FILE_DESCRIPTOR_STRATEGY),
                    new DefaultArtifactsToModuleDescriptorConverter(DefaultArtifactsToModuleDescriptorConverter.IVY_FILE_STRATEGY));

            return new ErrorHandlingArtifactPublisher(
                    new IvyBackedArtifactPublisher(
                            resolverProvider,
                            get(SettingsConverter.class),
                            get(PublishModuleDescriptorConverter.class),
                            fileModuleDescriptorConverter,
                            get(IvyFactory.class),
                            new DefaultIvyDependencyPublisher(
                                    new DefaultPublishOptionsFactory())));
        }
    }

    private static class DefaultArtifactPublicationServices implements ArtifactPublicationServices {
        private final DefaultDependencyResolutionServices dependencyResolutionServices;
        private DefaultRepositoryHandler repositoryHandler;
        private ArtifactPublisher artifactPublisher;

        public DefaultArtifactPublicationServices(DefaultDependencyResolutionServices dependencyResolutionServices) {
            this.dependencyResolutionServices = dependencyResolutionServices;
        }

        public ArtifactPublisher getArtifactPublisher() {
            if (artifactPublisher == null) {
                artifactPublisher = dependencyResolutionServices.createArtifactPublisher(getRepositoryHandler());
            }
            return artifactPublisher;
        }

        public DefaultRepositoryHandler getRepositoryHandler() {
            if (repositoryHandler == null) {
                repositoryHandler = dependencyResolutionServices.createRepositoryHandler();
            }
            return repositoryHandler;
        }
    }
}
