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

import org.gradle.StartParameter;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.dsl.ArtifactHandler;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.internal.ClassPathRegistry;
import org.gradle.api.internal.DomainObjectContext;
import org.gradle.api.internal.Factory;
import org.gradle.api.internal.Instantiator;
import org.gradle.api.internal.artifacts.configurations.ConfigurationContainerInternal;
import org.gradle.api.internal.artifacts.configurations.DefaultConfigurationContainer;
import org.gradle.api.internal.artifacts.configurations.DependencyMetaDataProvider;
import org.gradle.api.internal.artifacts.dependencies.DefaultExternalModuleDependency;
import org.gradle.api.internal.artifacts.dsl.DefaultArtifactHandler;
import org.gradle.api.internal.artifacts.dsl.DefaultPublishArtifactFactory;
import org.gradle.api.internal.artifacts.dsl.DefaultRepositoryHandler;
import org.gradle.api.internal.artifacts.dsl.dependencies.DefaultDependencyHandler;
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyFactory;
import org.gradle.api.internal.artifacts.dsl.dependencies.ProjectFinder;
import org.gradle.api.internal.artifacts.ivyservice.*;
import org.gradle.api.internal.artifacts.ivyservice.artifactcache.ArtifactResolutionCache;
import org.gradle.api.internal.artifacts.ivyservice.artifactcache.SingleFileBackedArtifactResolutionCache;
import org.gradle.api.internal.artifacts.ivyservice.clientmodule.ClientModuleRegistry;
import org.gradle.api.internal.artifacts.ivyservice.clientmodule.DefaultClientModuleRegistry;
import org.gradle.api.internal.artifacts.ivyservice.dynamicversions.ModuleResolutionCache;
import org.gradle.api.internal.artifacts.ivyservice.dynamicversions.SingleFileBackedModuleResolutionCache;
import org.gradle.api.internal.artifacts.ivyservice.filestore.DefaultFileStore;
import org.gradle.api.internal.artifacts.ivyservice.filestore.FileStore;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ResolveIvyFactory;
import org.gradle.api.internal.artifacts.ivyservice.modulecache.DefaultModuleDescriptorCache;
import org.gradle.api.internal.artifacts.ivyservice.modulecache.ModuleDescriptorCache;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.*;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.*;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.DefaultProjectModuleRegistry;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.DefaultDependencyResolver;
import org.gradle.api.internal.artifacts.mvnsettings.DefaultLocalMavenCacheLocator;
import org.gradle.api.internal.artifacts.repositories.DefaultResolverFactory;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.file.IdentityFileResolver;
import org.gradle.api.internal.notations.*;
import org.gradle.api.internal.notations.api.NotationParser;
import org.gradle.api.internal.project.DefaultServiceRegistry;
import org.gradle.api.internal.project.ServiceRegistry;
import org.gradle.cache.CacheRepository;
import org.gradle.listener.ListenerManager;
import org.gradle.logging.ProgressLoggerFactory;
import org.gradle.util.TimeProvider;
import org.gradle.util.WrapUtil;

import java.util.List;

public class DefaultDependencyManagementServices extends DefaultServiceRegistry implements DependencyManagementServices {

    public DefaultDependencyManagementServices(ServiceRegistry parent) {
        super(parent);
    }

    public DependencyResolutionServices create(FileResolver resolver, DependencyMetaDataProvider dependencyMetaDataProvider, ProjectFinder projectFinder, DomainObjectContext domainObjectContext) {
        return new DefaultDependencyResolutionServices(this, resolver, dependencyMetaDataProvider, projectFinder, domainObjectContext);
    }

    protected ResolveModuleDescriptorConverter createResolveModuleDescriptorConverter() {
        DependencyDescriptorFactory dependencyDescriptorFactoryDelegate = get(DependencyDescriptorFactory.class);
        return new ResolveModuleDescriptorConverter(
                get(ModuleDescriptorFactory.class),
                get(ConfigurationsToModuleDescriptorConverter.class),
                new DefaultDependenciesToModuleDescriptorConverter(
                        dependencyDescriptorFactoryDelegate,
                        get(ExcludeRuleConverter.class)));
        
    }
    protected PublishModuleDescriptorConverter createPublishModuleDescriptorConverter() {
        return new PublishModuleDescriptorConverter(
                get(ResolveModuleDescriptorConverter.class),
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

    protected DependencyDescriptorFactory createDependencyDescriptorFactory() {
        DefaultModuleDescriptorFactoryForClientModule clientModuleDescriptorFactory = new DefaultModuleDescriptorFactoryForClientModule();
        DependencyDescriptorFactory dependencyDescriptorFactoryDelegate = new DependencyDescriptorFactoryDelegate(
                new ClientModuleDependencyDescriptorFactory(
                        get(ExcludeRuleConverter.class),
                        clientModuleDescriptorFactory,
                        get(ClientModuleRegistry.class)),
                new ProjectDependencyDescriptorFactory(
                        get(ExcludeRuleConverter.class)),
                get(ExternalModuleDependencyDescriptorFactory.class));
        clientModuleDescriptorFactory.setDependencyDescriptorFactory(dependencyDescriptorFactoryDelegate);
        return dependencyDescriptorFactoryDelegate;
    }

    protected DependencyFactory createDependencyFactory() {
        Instantiator instantiator = get(Instantiator.class);

        ProjectDependencyFactory projectDependencyFactory = new ProjectDependencyFactory(
                get(StartParameter.class).getProjectDependenciesBuildInstruction(),
                instantiator);

        DependencyProjectNotationParser projParser = new DependencyProjectNotationParser(
                get(StartParameter.class).getProjectDependenciesBuildInstruction(),
                instantiator);

        NotationParser<? extends Dependency> moduleMapParser = new DependencyMapNotationParser<DefaultExternalModuleDependency>(instantiator, DefaultExternalModuleDependency.class);
        NotationParser<? extends Dependency> moduleStringParser = new DependencyStringNotationParser<DefaultExternalModuleDependency>(instantiator, DefaultExternalModuleDependency.class);
        NotationParser<? extends Dependency> selfResolvingDependencyFactory = new DependencyFilesNotationParser(instantiator);

        List<NotationParser<? extends Dependency>> notationParsers = WrapUtil.toList(
                moduleStringParser,
                moduleMapParser,
                selfResolvingDependencyFactory,
                projParser,
                new DependencyClassPathNotationParser(instantiator, get(ClassPathRegistry.class), new IdentityFileResolver()));

        DependencyNotationParser dependencyNotationParser = new DependencyNotationParser(notationParsers);

        return new DefaultDependencyFactory(
                dependencyNotationParser,
                new ClientModuleNotationParser(instantiator),
                projectDependencyFactory);
    }

    protected CacheLockingManager createCacheLockingManager() {
        return new DefaultCacheLockingManager(
                get(CacheRepository.class)
        );
    }
    
    protected ModuleResolutionCache createModuleResolutionCache() {
        return new SingleFileBackedModuleResolutionCache(
                get(ArtifactCacheMetaData.class),
                get(TimeProvider.class),
                get(CacheLockingManager.class)
        );
    }

    protected ModuleDescriptorCache createModuleDescriptorCache() {
        return new DefaultModuleDescriptorCache(
                get(ArtifactCacheMetaData.class),
                get(TimeProvider.class),
                get(CacheLockingManager.class),
                get(ArtifactResolutionCache.class));
    }

    protected ArtifactResolutionCache createArtifactResolutionCache() {
        return new SingleFileBackedArtifactResolutionCache(
                get(ArtifactCacheMetaData.class),
                get(TimeProvider.class),
                get(CacheLockingManager.class)
        );
    }
    
    protected FileStore createFileStore() {
        return new DefaultFileStore(
                get(ArtifactCacheMetaData.class)
        );
    }

    protected SettingsConverter createSettingsConverter() {
        return new DefaultSettingsConverter(
                get(ProgressLoggerFactory.class),
                new IvySettingsFactory(
                        get(ArtifactCacheMetaData.class),
                        get(FileStore.class)),
                get(ModuleResolutionCache.class),
                get(ModuleDescriptorCache.class),
                get(CacheLockingManager.class)
        );
    }

    protected IvyFactory createIvyFactory() {
        return  new DefaultIvyFactory();
    }
    
    protected ClientModuleRegistry createClientModuleRegistry() {
        return new DefaultClientModuleRegistry();
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
        private DefaultArtifactHandler artifactHandler;

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

        public ArtifactHandler getArtifactHandler() {
            if (artifactHandler == null) {
                artifactHandler = new DefaultArtifactHandler(
                        getConfigurationContainer(),
                        new DefaultPublishArtifactFactory(
                                get(Instantiator.class),
                                dependencyMetaDataProvider));
            }
            return artifactHandler;
        }

        public Factory<ArtifactPublicationServices> getPublishServicesFactory() {
            return new Factory<ArtifactPublicationServices>() {
                public ArtifactPublicationServices create() {
                    return new DefaultArtifactPublicationServices(DefaultDependencyResolutionServices.this);
                }
            };
        }

        ArtifactDependencyResolver createDependencyResolver(DefaultRepositoryHandler resolverProvider) {
            ResolveIvyFactory ivyFactory = new ResolveIvyFactory(
                    get(IvyFactory.class),
                    resolverProvider,
                    get(SettingsConverter.class),
                    get(ArtifactResolutionCache.class));

            ResolvedArtifactFactory resolvedArtifactFactory = new ResolvedArtifactFactory(
                    get(CacheLockingManager.class)
            );

            ArtifactDependencyResolver resolver = new DefaultDependencyResolver(
                    ivyFactory,
                    get(PublishModuleDescriptorConverter.class),
                    resolvedArtifactFactory,
                    new DefaultProjectModuleRegistry(
                            get(PublishModuleDescriptorConverter.class)),
                    get(ClientModuleRegistry.class));
            return new ErrorHandlingArtifactDependencyResolver(
                    new EventBroadcastingArtifactDependencyResolver(
                            new ShortcircuitEmptyConfigsArtifactDependencyResolver(
                                    new SelfResolvingDependencyResolver(
                                            new CacheLockingArtifactDependencyResolver(
                                                    get(CacheLockingManager.class),
                                                    resolver)))));
        }

        ArtifactPublisher createArtifactPublisher(DefaultRepositoryHandler resolverProvider) {
            PublishModuleDescriptorConverter fileModuleDescriptorConverter = new PublishModuleDescriptorConverter(
                    get(ResolveModuleDescriptorConverter.class),
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
