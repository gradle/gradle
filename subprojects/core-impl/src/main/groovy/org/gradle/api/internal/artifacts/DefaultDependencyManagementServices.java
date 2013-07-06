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
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.artifacts.dsl.ArtifactHandler;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.internal.ClassPathRegistry;
import org.gradle.api.internal.DomainObjectContext;
import org.gradle.api.internal.artifacts.configurations.ConfigurationContainerInternal;
import org.gradle.api.internal.artifacts.configurations.DefaultConfigurationContainer;
import org.gradle.api.internal.artifacts.configurations.DependencyMetaDataProvider;
import org.gradle.api.internal.artifacts.dependencies.DefaultExternalModuleDependency;
import org.gradle.api.internal.artifacts.dsl.DefaultArtifactHandler;
import org.gradle.api.internal.artifacts.dsl.DefaultRepositoryHandler;
import org.gradle.api.internal.artifacts.dsl.PublishArtifactNotationParserFactory;
import org.gradle.api.internal.artifacts.dsl.dependencies.DefaultDependencyHandler;
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyFactory;
import org.gradle.api.internal.artifacts.dsl.dependencies.ProjectFinder;
import org.gradle.api.internal.artifacts.ivyservice.*;
import org.gradle.api.internal.artifacts.ivyservice.dynamicversions.ModuleResolutionCache;
import org.gradle.api.internal.artifacts.ivyservice.dynamicversions.SingleFileBackedModuleResolutionCache;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ResolveIvyFactory;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.StartParameterResolutionOverride;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.memcache.InMemoryDependencyMetadataCache;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.DefaultMetaDataParser;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.ParserRegistry;
import org.gradle.api.internal.artifacts.ivyservice.modulecache.DefaultModuleDescriptorCache;
import org.gradle.api.internal.artifacts.ivyservice.modulecache.ModuleDescriptorCache;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.*;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.*;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.DefaultProjectModuleRegistry;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.DefaultDependencyResolver;
import org.gradle.api.internal.artifacts.mvnsettings.*;
import org.gradle.api.internal.artifacts.repositories.DefaultBaseRepositoryFactory;
import org.gradle.api.internal.artifacts.repositories.cachemanager.DownloadingRepositoryCacheManager;
import org.gradle.api.internal.artifacts.repositories.cachemanager.LocalFileRepositoryCacheManager;
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransportFactory;
import org.gradle.api.internal.externalresource.cached.ByUrlCachedExternalResourceIndex;
import org.gradle.api.internal.externalresource.ivy.ArtifactAtRepositoryCachedArtifactIndex;
import org.gradle.api.internal.externalresource.local.LocallyAvailableResourceFinder;
import org.gradle.api.internal.externalresource.local.ivy.LocallyAvailableResourceFinderFactory;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.file.IdentityFileResolver;
import org.gradle.api.internal.file.TmpDirTemporaryFileProvider;
import org.gradle.api.internal.filestore.PathKeyFileStore;
import org.gradle.api.internal.filestore.UniquePathKeyFileStore;
import org.gradle.api.internal.filestore.ivy.ArtifactRevisionIdFileStore;
import org.gradle.api.internal.notations.*;
import org.gradle.api.internal.notations.api.NotationParser;
import org.gradle.cache.CacheRepository;
import org.gradle.initialization.ProjectAccessListener;
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
        return new ResolveModuleDescriptorConverter(
                get(ModuleDescriptorFactory.class),
                get(ConfigurationsToModuleDescriptorConverter.class),
                new DefaultDependenciesToModuleDescriptorConverter(
                        get(DependencyDescriptorFactory.class),
                        get(ExcludeRuleConverter.class)));

    }

    protected PublishModuleDescriptorConverter createPublishModuleDescriptorConverter() {
        return new PublishModuleDescriptorConverter(
                get(ResolveModuleDescriptorConverter.class),
                new DefaultArtifactsToModuleDescriptorConverter());
    }

    protected ModuleDescriptorFactory createModuleDescriptorFactory() {
        return new DefaultModuleDescriptorFactory();
    }

    protected ExcludeRuleConverter createExcludeRuleConverter() {
        return new DefaultExcludeRuleConverter();
    }

    protected ExternalModuleIvyDependencyDescriptorFactory createExternalModuleDependencyDescriptorFactory() {
        return new ExternalModuleIvyDependencyDescriptorFactory(get(ExcludeRuleConverter.class));
    }

    protected ConfigurationsToModuleDescriptorConverter createConfigurationsToModuleDescriptorConverter() {
        return new DefaultConfigurationsToModuleDescriptorConverter();
    }

    protected DependencyDescriptorFactory createDependencyDescriptorFactory() {
        DefaultModuleDescriptorFactoryForClientModule clientModuleDescriptorFactory = new DefaultModuleDescriptorFactoryForClientModule();
        DependencyDescriptorFactory dependencyDescriptorFactory = new DefaultDependencyDescriptorFactory(
                new ClientModuleIvyDependencyDescriptorFactory(
                        get(ExcludeRuleConverter.class),
                        clientModuleDescriptorFactory
                ),
                new ProjectIvyDependencyDescriptorFactory(
                        get(ExcludeRuleConverter.class)),
                get(ExternalModuleIvyDependencyDescriptorFactory.class));
        clientModuleDescriptorFactory.setDependencyDescriptorFactory(dependencyDescriptorFactory);
        return dependencyDescriptorFactory;
    }

    protected DependencyFactory createDependencyFactory() {
        Instantiator instantiator = get(Instantiator.class);

        DefaultProjectDependencyFactory factory = new DefaultProjectDependencyFactory(
                get(ProjectAccessListener.class), instantiator, get(StartParameter.class).isBuildProjectDependencies());

        ProjectDependencyFactory projectDependencyFactory = new ProjectDependencyFactory(factory);
        DependencyProjectNotationParser projParser = new DependencyProjectNotationParser(factory);

        NotationParser<? extends Dependency> moduleMapParser = new DependencyMapNotationParser<DefaultExternalModuleDependency>(instantiator, DefaultExternalModuleDependency.class);
        NotationParser<? extends Dependency> moduleStringParser = new DependencyStringNotationParser<DefaultExternalModuleDependency>(instantiator, DefaultExternalModuleDependency.class);
        NotationParser<? extends Dependency> selfResolvingDependencyFactory = new DependencyFilesNotationParser(instantiator);

        List<NotationParser<? extends Dependency>> notationParsers = WrapUtil.toList(
                moduleStringParser,
                moduleMapParser,
                selfResolvingDependencyFactory,
                projParser,
                new DependencyClassPathNotationParser(instantiator, get(ClassPathRegistry.class), new IdentityFileResolver()));

        return new DefaultDependencyFactory(
                new DependencyNotationParser(notationParsers),
                new ClientModuleNotationParserFactory(instantiator).create(),
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

    protected ArtifactAtRepositoryCachedArtifactIndex createArtifactAtRepositoryCachedResolutionIndex() {
        return new ArtifactAtRepositoryCachedArtifactIndex(new File(get(ArtifactCacheMetaData.class).getCacheDir(), "artifact-at-repository.bin"),
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
        return new ArtifactRevisionIdFileStore(get(PathKeyFileStore.class), new TmpDirTemporaryFileProvider());
    }

    protected IvyContextManager createIvyContextManager() {
        return new DefaultIvyContextManager();
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

    protected LocalFileRepositoryCacheManager createLocalRepositoryCacheManager() {
        return new LocalFileRepositoryCacheManager("local");
    }

    protected DownloadingRepositoryCacheManager createDownloadingRepositoryCacheManager() {
        return new DownloadingRepositoryCacheManager("downloading", get(ArtifactRevisionIdFileStore.class), get(ByUrlCachedExternalResourceIndex.class),
                new TmpDirTemporaryFileProvider(), get(CacheLockingManager.class));
    }

    protected RepositoryTransportFactory createRepositoryTransportFactory() {
        return new RepositoryTransportFactory(
                get(ProgressLoggerFactory.class),
                get(LocalFileRepositoryCacheManager.class),
                get(DownloadingRepositoryCacheManager.class),
                new TmpDirTemporaryFileProvider(),
                get(ByUrlCachedExternalResourceIndex.class)
        );
    }

    protected ResolveIvyFactory createResolveIvyFactory() {
        StartParameter startParameter = get(StartParameter.class);
        StartParameterResolutionOverride startParameterResolutionOverride = new StartParameterResolutionOverride(startParameter);
        return new ResolveIvyFactory(
                get(ModuleResolutionCache.class),
                get(ModuleDescriptorCache.class),
                get(ArtifactAtRepositoryCachedArtifactIndex.class),
                get(CacheLockingManager.class),
                startParameterResolutionOverride,
                get(BuildCommencedTimeProvider.class),
                get(TopLevelDependencyManagementServices.class).get(InMemoryDependencyMetadataCache.class));
    }

    protected ArtifactDependencyResolver createArtifactDependencyResolver() {
        ArtifactDependencyResolver resolver = new DefaultDependencyResolver(
                get(ResolveIvyFactory.class),
                get(PublishModuleDescriptorConverter.class),
                new ResolvedArtifactFactory(
                        get(CacheLockingManager.class)
                ),
                new DefaultProjectModuleRegistry(
                        get(PublishModuleDescriptorConverter.class)),
                get(CacheLockingManager.class),
                get(IvyContextManager.class)
        );
        return new ErrorHandlingArtifactDependencyResolver(
                new ShortcircuitEmptyConfigsArtifactDependencyResolver(
                        new SelfResolvingDependencyResolver(
                                new CacheLockingArtifactDependencyResolver(
                                        get(CacheLockingManager.class),
                                        resolver))));
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
                        get(ProgressLoggerFactory.class),
                        get(LocalFileRepositoryCacheManager.class),
                        get(DownloadingRepositoryCacheManager.class),
                        new DefaultMetaDataParser(new ParserRegistry())
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
                dependencyHandler = new DefaultDependencyHandler(getConfigurationContainer(), parent.get(DependencyFactory.class), projectFinder);
            }
            return dependencyHandler;
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
