/*
 * Copyright 2013 the original author or authors.
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
import org.gradle.api.internal.ClassPathRegistry;
import org.gradle.api.internal.artifacts.component.ComponentIdentifierFactory;
import org.gradle.api.internal.artifacts.dependencies.DefaultExternalModuleDependency;
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyFactory;
import org.gradle.api.internal.artifacts.ivyservice.*;
import org.gradle.api.internal.artifacts.ivyservice.dynamicversions.ModuleVersionsCache;
import org.gradle.api.internal.artifacts.ivyservice.dynamicversions.SingleFileBackedModuleVersionsCache;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ResolveIvyFactory;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.StartParameterResolutionOverride;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.memcache.InMemoryDependencyMetadataCache;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.LatestStrategy;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.LatestVersionStrategy;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.ResolverStrategy;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionMatcher;
import org.gradle.api.internal.artifacts.ivyservice.modulecache.DefaultModuleArtifactsCache;
import org.gradle.api.internal.artifacts.ivyservice.modulecache.DefaultModuleMetaDataCache;
import org.gradle.api.internal.artifacts.ivyservice.modulecache.ModuleArtifactsCache;
import org.gradle.api.internal.artifacts.ivyservice.modulecache.ModuleMetaDataCache;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.PublishLocalComponentFactory;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.DefaultProjectComponentRegistry;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.DefaultProjectPublicationRegistry;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectPublicationRegistry;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.DefaultDependencyResolver;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.store.ResolutionResultsStoreFactory;
import org.gradle.api.internal.artifacts.metadata.ModuleVersionArtifactMetaData;
import org.gradle.api.internal.artifacts.mvnsettings.*;
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
import org.gradle.api.internal.file.FileLookup;
import org.gradle.api.internal.file.TemporaryFileProvider;
import org.gradle.api.internal.file.TmpDirTemporaryFileProvider;
import org.gradle.api.internal.filestore.UniquePathKeyFileStore;
import org.gradle.api.internal.filestore.ivy.ArtifactIdentifierFileStore;
import org.gradle.api.internal.notations.*;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.ProjectRegistry;
import org.gradle.cache.CacheRepository;
import org.gradle.initialization.ProjectAccessListener;
import org.gradle.internal.SystemProperties;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.typeconversion.NotationParser;
import org.gradle.logging.ProgressLoggerFactory;
import org.gradle.util.BuildCommencedTimeProvider;

import java.util.Arrays;
import java.util.List;

/**
 * The set of dependency management services that are created per build.
 */
class DependencyManagementBuildScopeServices {
    InMemoryDependencyMetadataCache createInMemoryDependencyMetadataCache() {
        return new InMemoryDependencyMetadataCache();
    }

    DependencyManagementServices createDependencyManagementServices(ServiceRegistry parent) {
        return new DefaultDependencyManagementServices(parent);
    }

    DependencyFactory createDependencyFactory(Instantiator instantiator,
                                              ProjectAccessListener projectAccessListener,
                                              StartParameter startParameter,
                                              ClassPathRegistry classPathRegistry,
                                              FileLookup fileLookup) {
        DefaultProjectDependencyFactory factory = new DefaultProjectDependencyFactory(
                projectAccessListener, instantiator, startParameter.isBuildProjectDependencies());

        ProjectDependencyFactory projectDependencyFactory = new ProjectDependencyFactory(factory);
        DependencyProjectNotationParser projParser = new DependencyProjectNotationParser(factory);

        NotationParser<Object, ? extends Dependency> moduleMapParser = new DependencyMapNotationParser<DefaultExternalModuleDependency>(instantiator, DefaultExternalModuleDependency.class);
        NotationParser<Object, ? extends Dependency> moduleStringParser = new DependencyStringNotationParser<DefaultExternalModuleDependency>(instantiator, DefaultExternalModuleDependency.class);
        NotationParser<Object, ? extends Dependency> selfResolvingDependencyFactory = new DependencyFilesNotationParser(instantiator);

        List<NotationParser<Object, ? extends Dependency>> notationParsers = Arrays.asList(
                moduleStringParser,
                moduleMapParser,
                selfResolvingDependencyFactory,
                projParser,
                new DependencyClassPathNotationParser(instantiator, classPathRegistry, fileLookup.getFileResolver()));

        return new DefaultDependencyFactory(
                new DependencyNotationParser(notationParsers),
                new ClientModuleNotationParserFactory(instantiator).create(),
                projectDependencyFactory);
    }

    CacheLockingManager createCacheLockingManager(CacheRepository cacheRepository) {
        return new DefaultCacheLockingManager(cacheRepository);
    }

    BuildCommencedTimeProvider createBuildTimeProvider() {
        return new BuildCommencedTimeProvider();
    }

    ModuleVersionsCache createModuleVersionsCache(BuildCommencedTimeProvider timeProvider, CacheLockingManager cacheLockingManager) {
        return new SingleFileBackedModuleVersionsCache(
                timeProvider,
                cacheLockingManager
        );
    }

    ModuleArtifactsCache createModuleArtifactsCache(BuildCommencedTimeProvider timeProvider, CacheLockingManager cacheLockingManager) {
        return new DefaultModuleArtifactsCache(
                timeProvider,
                cacheLockingManager
        );
    }

    ModuleMetaDataCache createModuleDescriptorCache(BuildCommencedTimeProvider timeProvider, CacheLockingManager cacheLockingManager, ResolverStrategy resolverStrategy) {
        return new DefaultModuleMetaDataCache(
                timeProvider,
                cacheLockingManager,
                resolverStrategy
        );
    }

    ArtifactAtRepositoryCachedArtifactIndex createArtifactAtRepositoryCachedResolutionIndex(BuildCommencedTimeProvider timeProvider, CacheLockingManager cacheLockingManager) {
        return new ArtifactAtRepositoryCachedArtifactIndex(
                "artifact-at-repository",
                timeProvider,
                cacheLockingManager
        );
    }

    ByUrlCachedExternalResourceIndex createArtifactUrlCachedResolutionIndex(BuildCommencedTimeProvider timeProvider, CacheLockingManager cacheLockingManager) {
        return new ByUrlCachedExternalResourceIndex(
                "artifact-at-url",
                timeProvider,
                cacheLockingManager
        );
    }

    ArtifactIdentifierFileStore createArtifactRevisionIdFileStore(CacheLockingManager cacheLockingManager) {
        return new ArtifactIdentifierFileStore(new UniquePathKeyFileStore(cacheLockingManager.getFileStoreDirectory()), new TmpDirTemporaryFileProvider());
    }

    MavenSettingsProvider createMavenSettingsProvider() {
        return new DefaultMavenSettingsProvider(new DefaultMavenFileLocations());
    }

    LocalMavenRepositoryLocator createLocalMavenRepositoryLocator(MavenSettingsProvider mavenSettingsProvider) {
        return new DefaultLocalMavenRepositoryLocator(mavenSettingsProvider, SystemProperties.asMap(), System.getenv());
    }

    LocallyAvailableResourceFinder<ModuleVersionArtifactMetaData> createArtifactRevisionIdLocallyAvailableResourceFinder(ArtifactCacheMetaData artifactCacheMetaData, LocalMavenRepositoryLocator localMavenRepositoryLocator, ArtifactIdentifierFileStore fileStore) {
        LocallyAvailableResourceFinderFactory finderFactory = new LocallyAvailableResourceFinderFactory(
                artifactCacheMetaData,
                localMavenRepositoryLocator,
                fileStore);
        return finderFactory.create();
    }

    ResolverStrategy createResolverStrategy() {
        return new ResolverStrategy();
    }

    VersionMatcher createVersionMatcher(ResolverStrategy resolverStrategy) {
        return resolverStrategy.getVersionMatcher();
    }

    LatestStrategy createLatestStrategy(VersionMatcher versionMatcher) {
        return new LatestVersionStrategy(versionMatcher);
    }

    LocalFileRepositoryArtifactCache createLocalRepositoryArtifactCache() {
        return new LocalFileRepositoryArtifactCache();
    }

    DownloadingRepositoryArtifactCache createDownloadingRepositoryArtifactCache(ArtifactIdentifierFileStore artifactIdentifierFileStore, ByUrlCachedExternalResourceIndex externalResourceIndex,
                                                                                TemporaryFileProvider temporaryFileProvider, CacheLockingManager cacheLockingManager) {
        return new DownloadingRepositoryArtifactCache(artifactIdentifierFileStore,
                externalResourceIndex,
                temporaryFileProvider,
                cacheLockingManager);
    }

    RepositoryTransportFactory createRepositoryTransportFactory(ProgressLoggerFactory progressLoggerFactory, LocalFileRepositoryArtifactCache localFileRepositoryArtifactCache,
                                                                DownloadingRepositoryArtifactCache downloadingRepositoryArtifactCache, TemporaryFileProvider temporaryFileProvider,
                                                                ByUrlCachedExternalResourceIndex externalResourceIndex, BuildCommencedTimeProvider buildCommencedTimeProvider) {
        return new RepositoryTransportFactory(
                progressLoggerFactory,
                localFileRepositoryArtifactCache,
                downloadingRepositoryArtifactCache,
                temporaryFileProvider,
                externalResourceIndex,
                buildCommencedTimeProvider
        );
    }

    LegacyDependencyResolverRepositoryFactory createCustomerResolverRepositoryFactory(ProgressLoggerFactory progressLoggerFactory, ArtifactIdentifierFileStore artifactIdentifierFileStore,
                                                                                      TemporaryFileProvider temporaryFileProvider, CacheLockingManager cacheLockingManager) {
        return new CustomIvyResolverRepositoryFactory(
                progressLoggerFactory,
                new LocalFileRepositoryCacheManager("local"),
                new DownloadingRepositoryCacheManager(
                        "downloading",
                        artifactIdentifierFileStore,
                        temporaryFileProvider,
                        cacheLockingManager
                )
        );
    }

    ResolveIvyFactory createResolveIvyFactory(StartParameter startParameter, ModuleVersionsCache moduleVersionsCache, ModuleMetaDataCache moduleMetaDataCache, ModuleArtifactsCache moduleArtifactsCache,
                                              ArtifactAtRepositoryCachedArtifactIndex artifactAtRepositoryCachedArtifactIndex, CacheLockingManager cacheLockingManager,
                                              BuildCommencedTimeProvider buildCommencedTimeProvider, InMemoryDependencyMetadataCache inMemoryDependencyMetadataCache,
                                              VersionMatcher versionMatcher, LatestStrategy latestStrategy) {
        StartParameterResolutionOverride startParameterResolutionOverride = new StartParameterResolutionOverride(startParameter);
        return new ResolveIvyFactory(
                moduleVersionsCache,
                moduleMetaDataCache,
                moduleArtifactsCache,
                artifactAtRepositoryCachedArtifactIndex,
                cacheLockingManager,
                startParameterResolutionOverride,
                buildCommencedTimeProvider,
                inMemoryDependencyMetadataCache,
                versionMatcher,
                latestStrategy);
    }

    ArtifactDependencyResolver createArtifactDependencyResolver(ResolveIvyFactory resolveIvyFactory, PublishLocalComponentFactory publishModuleDescriptorConverter,
                                                                CacheLockingManager cacheLockingManager, IvyContextManager ivyContextManager, ResolutionResultsStoreFactory resolutionResultsStoreFactory,
                                                                VersionMatcher versionMatcher, LatestStrategy latestStrategy, ProjectRegistry<ProjectInternal> projectRegistry,
                                                                ComponentIdentifierFactory componentIdentifierFactory) {
        ArtifactDependencyResolver resolver = new DefaultDependencyResolver(
                resolveIvyFactory,
                publishModuleDescriptorConverter,
                new DefaultProjectComponentRegistry(
                        publishModuleDescriptorConverter,
                        projectRegistry),
                cacheLockingManager,
                ivyContextManager,
                resolutionResultsStoreFactory,
                versionMatcher,
                latestStrategy);
        return new ErrorHandlingArtifactDependencyResolver(
                new ShortcircuitEmptyConfigsArtifactDependencyResolver(
                        new SelfResolvingDependencyResolver(
                                new CacheLockingArtifactDependencyResolver(
                                        cacheLockingManager,
                                        resolver)),
                        componentIdentifierFactory));
    }

    ResolutionResultsStoreFactory createResolutionResultsStoreFactory(TemporaryFileProvider temporaryFileProvider) {
        return new ResolutionResultsStoreFactory(temporaryFileProvider);
    }

    ProjectPublicationRegistry createProjectPublicationRegistry() {
        return new DefaultProjectPublicationRegistry();
    }
}
