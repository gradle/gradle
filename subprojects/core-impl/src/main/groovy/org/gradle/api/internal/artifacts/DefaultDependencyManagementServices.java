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
import org.gradle.api.internal.artifacts.ivyservice.dynamicversions.ModuleResolutionCache;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ResolveIvyFactory;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.StartParameterResolutionOverride;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.memcache.InMemoryDependencyMetadataCache;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.LatestStrategy;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.LatestVersionStrategy;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.ResolverStrategy;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionMatcher;
import org.gradle.api.internal.artifacts.ivyservice.modulecache.ModuleMetaDataCache;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.PublishModuleDescriptorConverter;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.DefaultProjectModuleRegistry;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.DefaultDependencyResolver;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.store.ResolutionResultsStoreFactory;
import org.gradle.api.internal.artifacts.mvnsettings.*;
import org.gradle.api.internal.artifacts.repositories.DefaultBaseRepositoryFactory;
import org.gradle.api.internal.artifacts.repositories.cachemanager.DownloadingRepositoryArtifactCache;
import org.gradle.api.internal.artifacts.repositories.cachemanager.LocalFileRepositoryArtifactCache;
import org.gradle.api.internal.artifacts.repositories.legacy.CustomIvyResolverRepositoryFactory;
import org.gradle.api.internal.artifacts.repositories.legacy.DownloadingRepositoryCacheManager;
import org.gradle.api.internal.artifacts.repositories.legacy.LegacyDependencyResolverRepositoryFactory;
import org.gradle.api.internal.artifacts.repositories.legacy.LocalFileRepositoryCacheManager;
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransportFactory;
import org.gradle.api.internal.externalresource.cached.ByUrlCachedExternalResourceIndex;
import org.gradle.api.internal.externalresource.ivy.ArtifactAtRepositoryCachedArtifactIndex;
import org.gradle.api.internal.externalresource.local.LocallyAvailableResourceFinder;
import org.gradle.api.internal.externalresource.local.ivy.LocallyAvailableResourceFinderFactory;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.file.TmpDirTemporaryFileProvider;
import org.gradle.api.internal.filestore.ivy.ArtifactRevisionIdFileStore;
import org.gradle.api.internal.notations.api.NotationParser;
import org.gradle.internal.SystemProperties;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.service.DefaultServiceRegistry;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.listener.ListenerManager;
import org.gradle.logging.ProgressLoggerFactory;
import org.gradle.util.BuildCommencedTimeProvider;

public class DefaultDependencyManagementServices extends DefaultServiceRegistry implements DependencyManagementServices {

    public DefaultDependencyManagementServices(ServiceRegistry parent) {
        super(parent);
    }

    public DependencyResolutionServices create(FileResolver resolver, DependencyMetaDataProvider dependencyMetaDataProvider, ProjectFinder projectFinder, DomainObjectContext domainObjectContext) {
        return new DefaultDependencyResolutionServices(this, resolver, dependencyMetaDataProvider, projectFinder, domainObjectContext);
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

    protected LegacyDependencyResolverRepositoryFactory createCustomerResolverRepositoryFactory() {
        return new CustomIvyResolverRepositoryFactory(
                get(ProgressLoggerFactory.class),
                new LocalFileRepositoryCacheManager("local"),
                new DownloadingRepositoryCacheManager(
                        "downloading",
                        get(ArtifactRevisionIdFileStore.class),
                        new TmpDirTemporaryFileProvider(),
                        get(CacheLockingManager.class)
                )
        );
    }

    protected LocalFileRepositoryArtifactCache createLocalRepositoryArtifactCache() {
        return new LocalFileRepositoryArtifactCache();
    }

    protected DownloadingRepositoryArtifactCache createDownloadingRepositoryArtifactCache() {
        return new DownloadingRepositoryArtifactCache(get(ArtifactRevisionIdFileStore.class), get(ByUrlCachedExternalResourceIndex.class),
                new TmpDirTemporaryFileProvider(), get(CacheLockingManager.class));
    }

    protected RepositoryTransportFactory createRepositoryTransportFactory() {
        return new RepositoryTransportFactory(
                get(ProgressLoggerFactory.class),
                get(LocalFileRepositoryArtifactCache.class),
                get(DownloadingRepositoryArtifactCache.class),
                new TmpDirTemporaryFileProvider(),
                get(ByUrlCachedExternalResourceIndex.class)
        );
    }

    protected ResolveIvyFactory createResolveIvyFactory() {
        StartParameter startParameter = get(StartParameter.class);
        StartParameterResolutionOverride startParameterResolutionOverride = new StartParameterResolutionOverride(startParameter);
        return new ResolveIvyFactory(
                get(ModuleResolutionCache.class),
                get(ModuleMetaDataCache.class),
                get(ArtifactAtRepositoryCachedArtifactIndex.class),
                get(CacheLockingManager.class),
                startParameterResolutionOverride,
                get(BuildCommencedTimeProvider.class),
                get(InMemoryDependencyMetadataCache.class),
                get(VersionMatcher.class),
                get(LatestStrategy.class));
    }

    protected ArtifactDependencyResolver createArtifactDependencyResolver() {
        ArtifactDependencyResolver resolver = new DefaultDependencyResolver(
                get(ResolveIvyFactory.class),
                get(PublishModuleDescriptorConverter.class),
                new ResolvedArtifactFactory(
                        get(CacheLockingManager.class),
                        get(IvyContextManager.class)
                ),
                new DefaultProjectModuleRegistry(
                        get(PublishModuleDescriptorConverter.class)),
                get(CacheLockingManager.class),
                get(IvyContextManager.class),
                get(ResolutionResultsStoreFactory.class));
        return new ErrorHandlingArtifactDependencyResolver(
                new ShortcircuitEmptyConfigsArtifactDependencyResolver(
                        new SelfResolvingDependencyResolver(
                                new CacheLockingArtifactDependencyResolver(
                                        get(CacheLockingManager.class),
                                        resolver))));
    }

    protected ResolutionResultsStoreFactory createResolutionResultsStoreFactory() {
        return new ResolutionResultsStoreFactory(new TmpDirTemporaryFileProvider());
    }

    protected VersionMatcher createVersionMatcher() {
        return ResolverStrategy.INSTANCE.getVersionMatcher();
    }

    protected LatestStrategy createLatestStrategy() {
        return new LatestVersionStrategy(get(VersionMatcher.class));
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
                        get(LatestStrategy.class)
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
