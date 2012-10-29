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

import org.apache.ivy.core.module.id.ArtifactRevisionId;
import org.gradle.StartParameter;
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.dsl.ArtifactHandler;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.internal.ClassPathRegistry;
import org.gradle.api.internal.DomainObjectContext;
import org.gradle.api.internal.artifacts.configurations.ConfigurationContainerInternal;
import org.gradle.api.internal.artifacts.configurations.DefaultConfigurationContainer;
import org.gradle.api.internal.artifacts.configurations.DependencyMetaDataProvider;
import org.gradle.api.internal.artifacts.configurations.ResolverProvider;
import org.gradle.api.internal.artifacts.dependencies.DefaultExternalModuleDependency;
import org.gradle.api.internal.artifacts.dsl.*;
import org.gradle.api.internal.artifacts.dsl.dependencies.DefaultDependencyHandler;
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyFactory;
import org.gradle.api.internal.artifacts.dsl.dependencies.ProjectFinder;
import org.gradle.api.internal.artifacts.ivyservice.*;
import org.gradle.api.internal.artifacts.ivyservice.dynamicversions.ModuleResolutionCache;
import org.gradle.api.internal.artifacts.ivyservice.dynamicversions.SingleFileBackedModuleResolutionCache;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ResolveIvyFactory;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.StartParameterResolutionOverride;
import org.gradle.api.internal.artifacts.ivyservice.modulecache.DefaultModuleDescriptorCache;
import org.gradle.api.internal.artifacts.ivyservice.modulecache.ModuleDescriptorCache;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.*;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.*;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.DefaultProjectModuleRegistry;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.DefaultDependencyResolver;
import org.gradle.api.internal.artifacts.mvnsettings.*;
import org.gradle.api.internal.artifacts.repositories.DefaultBaseRepositoryFactory;
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransportFactory;
import org.gradle.api.internal.externalresource.cached.ByUrlCachedExternalResourceIndex;
import org.gradle.api.internal.externalresource.ivy.ArtifactAtRepositoryCachedExternalResourceIndex;
import org.gradle.api.internal.externalresource.local.LocallyAvailableResourceFinder;
import org.gradle.api.internal.externalresource.local.ivy.LocallyAvailableResourceFinderFactory;
import org.gradle.api.internal.file.DefaultTemporaryFileProvider;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.file.IdentityFileResolver;
import org.gradle.api.internal.filestore.PathKeyFileStore;
import org.gradle.api.internal.filestore.UniquePathKeyFileStore;
import org.gradle.api.internal.filestore.ivy.ArtifactRevisionIdFileStore;
import org.gradle.api.internal.notations.*;
import org.gradle.api.internal.notations.api.NotationParser;
import org.gradle.cache.CacheRepository;
import org.gradle.internal.Factory;
import org.gradle.internal.SystemProperties;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.service.DefaultServiceRegistry;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.listener.ListenerManager;
import org.gradle.logging.ProgressLoggerFactory;
import org.gradle.util.BuildCommencedTimeProvider;
import org.gradle.util.WrapUtil;

import java.io.File;
import java.util.List;

public class DefaultDependencyManagementServices extends DefaultServiceRegistry implements DependencyManagementServices {

    public DefaultDependencyManagementServices(ServiceRegistry parent) {
        super(parent);
    }

    public DependencyResolutionServices create(FileResolver resolver, DependencyMetaDataProvider dependencyMetaDataProvider, ProjectFinder projectFinder, DomainObjectContext domainObjectContext) {
        return new DefaultDependencyResolutionServices(this, resolver, dependencyMetaDataProvider, projectFinder, domainObjectContext);
    }

    protected ResolveModuleDescriptorConverter createResolveModuleDescriptorConverter() {
        DependencyDescriptorFactory dependencyDescriptorFactoryDelegate = get(DependencyDescriptorFactoryDelegate.class);
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
        return new DefaultModuleDescriptorFactory(get(IvyFactory.class), get(SettingsConverter.class));
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

    protected DependencyDescriptorFactoryDelegate createDependencyDescriptorFactory() {
        DefaultModuleDescriptorFactoryForClientModule clientModuleDescriptorFactory = new DefaultModuleDescriptorFactoryForClientModule();
        DependencyDescriptorFactoryDelegate dependencyDescriptorFactoryDelegate = new DependencyDescriptorFactoryDelegate(
                new ClientModuleDependencyDescriptorFactory(
                        get(ExcludeRuleConverter.class),
                        clientModuleDescriptorFactory
                ),
                new ProjectDependencyDescriptorFactory(
                        get(ExcludeRuleConverter.class)),
                get(ExternalModuleDependencyDescriptorFactory.class));
        clientModuleDescriptorFactory.setDependencyDescriptorFactory(dependencyDescriptorFactoryDelegate);
        return dependencyDescriptorFactoryDelegate;
    }

    protected DependencyFactory createDependencyFactory() {
        Instantiator instantiator = get(Instantiator.class);

        ProjectDependenciesBuildInstruction projectDependenciesBuildInstruction = new ProjectDependenciesBuildInstruction(get(StartParameter.class).isBuildProjectDependencies());

        ProjectDependencyFactory projectDependencyFactory = new ProjectDependencyFactory(
                projectDependenciesBuildInstruction,
                instantiator);

        DependencyProjectNotationParser projParser = new DependencyProjectNotationParser(
                projectDependenciesBuildInstruction,
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

    protected BuildCommencedTimeProvider createBuildTimeProvider() {
        return new BuildCommencedTimeProvider();
    }

    protected ModuleResolutionCache createModuleResolutionCache() {
        return new SingleFileBackedModuleResolutionCache(
                get(ArtifactCacheMetaData.class),
                get(BuildCommencedTimeProvider.class),
                get(CacheLockingManager.class)
        );
    }

    protected ModuleDescriptorCache createModuleDescriptorCache() {
        return new DefaultModuleDescriptorCache(
                get(ArtifactCacheMetaData.class),
                get(BuildCommencedTimeProvider.class),
                get(CacheLockingManager.class)
        );
    }

    protected ArtifactAtRepositoryCachedExternalResourceIndex createArtifactAtRepositoryCachedResolutionIndex() {
        return new ArtifactAtRepositoryCachedExternalResourceIndex(
                new File(get(ArtifactCacheMetaData.class).getCacheDir(), "artifact-at-repository.bin"),
                get(BuildCommencedTimeProvider.class),
                get(CacheLockingManager.class)
        );
    }

    protected ByUrlCachedExternalResourceIndex createArtifactUrlCachedResolutionIndex() {
        return new ByUrlCachedExternalResourceIndex(
                new File(get(ArtifactCacheMetaData.class).getCacheDir(), "artifact-at-url.bin"),
                get(BuildCommencedTimeProvider.class),
                get(CacheLockingManager.class)
        );
    }

    protected PathKeyFileStore createUniquePathFileStore() {
        return new UniquePathKeyFileStore(new File(get(ArtifactCacheMetaData.class).getCacheDir(), "filestore"));
    }

    protected ArtifactRevisionIdFileStore createArtifactRevisionIdFileStore() {
        return new ArtifactRevisionIdFileStore(get(PathKeyFileStore.class), new DefaultTemporaryFileProvider(new Factory<File>() {
            public File create() {
                return new File(get(PathKeyFileStore.class).getBaseDir(), "tmp");
            }
        }));
    }

    protected SettingsConverter createSettingsConverter() {
        return new DefaultSettingsConverter(
                new IvySettingsFactory(
                        get(ArtifactCacheMetaData.class)
                )
        );
    }

    protected IvyFactory createIvyFactory() {
        return new DefaultIvyFactory();
    }

    protected MavenSettingsProvider createMavenSettingsProvider() {
        return new DefaultMavenSettingsProvider(new DefaultMavenFileLocations());
    }

    protected LocalMavenRepositoryLocator createLocalMavenRepositoryLocator() {
        return new DefaultLocalMavenRepositoryLocator(get(MavenSettingsProvider.class), SystemProperties.asMap(), System.getenv());
    }

    protected LocallyAvailableResourceFinder<ArtifactRevisionId> createArtifactRevisionIdLocallyAvailableResourceFinder() {
        LocallyAvailableResourceFinderFactory finderFactory = new LocallyAvailableResourceFinderFactory(
                get(ArtifactCacheMetaData.class), get(LocalMavenRepositoryLocator.class), get(ArtifactRevisionIdFileStore.class)
        );
        return finderFactory.create();
    }

    protected RepositoryTransportFactory createRepositoryTransportFactory() {
        return new RepositoryTransportFactory(
                get(ProgressLoggerFactory.class), get(ArtifactRevisionIdFileStore.class), get(ByUrlCachedExternalResourceIndex.class)
        );
    }

    private class DefaultDependencyResolutionServices implements DependencyResolutionServices {
        private final ServiceRegistry parent;
        private final FileResolver fileResolver;
        private final DependencyMetaDataProvider dependencyMetaDataProvider;
        private final ProjectFinder projectFinder;
        private final DomainObjectContext domainObjectContext;
        private RepositoryFactoryInternal repositoryFactory;
        private DefaultRepositoryHandler repositoryHandler;
        private ConfigurationContainerInternal configurationContainer;
        private DependencyHandler dependencyHandler;
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

        public RepositoryFactoryInternal getRepositoryFactory() {
            if (repositoryFactory == null) {
                repositoryFactory = new DefaultRepositoryFactory(getBaseRepositoryFactory());
            }
            return repositoryFactory;
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
                        get(ByUrlCachedExternalResourceIndex.class),
                        new DefaultArtifactPublisherFactory(new Transformer<ArtifactPublisher, ResolverProvider>() {
                            public ArtifactPublisher transform(ResolverProvider resolverProvider) {
                                return createArtifactPublisher(resolverProvider);
                            }
                        })
                );
            }

            return baseRepositoryFactory;
        }

        private DefaultRepositoryHandler createRepositoryHandler() {
            Instantiator instantiator = parent.get(Instantiator.class);
            return instantiator.newInstance(DefaultRepositoryHandler.class, getRepositoryFactory(), instantiator);
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
            StartParameter startParameter = get(StartParameter.class);
            StartParameterResolutionOverride startParameterResolutionOverride = new StartParameterResolutionOverride(startParameter);
            ResolveIvyFactory ivyFactory = new ResolveIvyFactory(
                    get(IvyFactory.class),
                    resolverProvider,
                    get(SettingsConverter.class),
                    get(ModuleResolutionCache.class),
                    get(ModuleDescriptorCache.class),
                    get(ArtifactAtRepositoryCachedExternalResourceIndex.class),
                    get(CacheLockingManager.class),
                    startParameterResolutionOverride,
                    get(BuildCommencedTimeProvider.class));

            ResolvedArtifactFactory resolvedArtifactFactory = new ResolvedArtifactFactory(
                    get(CacheLockingManager.class)
            );

            ArtifactDependencyResolver resolver = new DefaultDependencyResolver(
                    ivyFactory,
                    get(PublishModuleDescriptorConverter.class),
                    resolvedArtifactFactory,
                    new DefaultProjectModuleRegistry(
                            get(PublishModuleDescriptorConverter.class))
            );
            return new ErrorHandlingArtifactDependencyResolver(
                    new ShortcircuitEmptyConfigsArtifactDependencyResolver(
                            new SelfResolvingDependencyResolver(
                                    new CacheLockingArtifactDependencyResolver(
                                            get(CacheLockingManager.class),
                                            resolver))));
        }

        ArtifactPublisher createArtifactPublisher(ResolverProvider resolverProvider) {
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
                            new DefaultIvyDependencyPublisher(),
                            new IvyXmlModuleDescriptorWriter()));
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
