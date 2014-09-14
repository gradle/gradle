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
import org.gradle.api.internal.ClassPathRegistry;
import org.gradle.api.internal.artifacts.component.ComponentIdentifierFactory;
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyFactory;
import org.gradle.api.internal.artifacts.ivyservice.*;
import org.gradle.api.internal.artifacts.ivyservice.dynamicversions.ModuleVersionsCache;
import org.gradle.api.internal.artifacts.ivyservice.dynamicversions.SingleFileBackedModuleVersionsCache;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ResolveIvyFactory;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.StartParameterResolutionOverride;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.memcache.InMemoryCachedRepositoryFactory;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.LatestStrategy;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.LatestVersionStrategy;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.ResolverStrategy;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionMatcher;
import org.gradle.api.internal.artifacts.ivyservice.modulecache.DefaultModuleArtifactsCache;
import org.gradle.api.internal.artifacts.ivyservice.modulecache.DefaultModuleMetaDataCache;
import org.gradle.api.internal.artifacts.ivyservice.modulecache.ModuleArtifactsCache;
import org.gradle.api.internal.artifacts.ivyservice.modulecache.ModuleMetaDataCache;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.DependencyDescriptorFactory;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.DefaultProjectComponentRegistry;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.DefaultProjectPublicationRegistry;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectPublicationRegistry;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.DefaultDependencyResolver;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.store.ResolutionResultsStoreFactory;
import org.gradle.api.internal.artifacts.mvnsettings.*;
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransportFactory;
import org.gradle.api.internal.file.FileLookup;
import org.gradle.api.internal.file.TemporaryFileProvider;
import org.gradle.api.internal.file.TmpDirTemporaryFileProvider;
import org.gradle.api.internal.filestore.ivy.ArtifactIdentifierFileStore;
import org.gradle.api.internal.notations.ClientModuleNotationParserFactory;
import org.gradle.api.internal.notations.DependencyNotationParser;
import org.gradle.api.internal.notations.ProjectDependencyFactory;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.ProjectRegistry;
import org.gradle.cache.CacheRepository;
import org.gradle.initialization.ProjectAccessListener;
import org.gradle.internal.SystemProperties;
import org.gradle.internal.component.external.model.ModuleComponentArtifactMetaData;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.resource.cached.ByUrlCachedExternalResourceIndex;
import org.gradle.internal.resource.cached.ivy.ArtifactAtRepositoryCachedArtifactIndex;
import org.gradle.internal.resource.local.LocallyAvailableResourceFinder;
import org.gradle.internal.resource.local.UniquePathKeyFileStore;
import org.gradle.internal.resource.local.ivy.LocallyAvailableResourceFinderFactory;
import org.gradle.internal.resource.transport.sftp.SftpClientFactory;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.logging.ProgressLoggerFactory;
import org.gradle.util.BuildCommencedTimeProvider;

/**
 * The set of dependency management services that are created per build.
 */
class DependencyManagementBuildScopeServices {
    InMemoryCachedRepositoryFactory createInMemoryDependencyMetadataCache() {
        return new InMemoryCachedRepositoryFactory();
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

        return new DefaultDependencyFactory(
                DependencyNotationParser.parser(instantiator, factory, classPathRegistry, fileLookup),
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

    LocallyAvailableResourceFinder<ModuleComponentArtifactMetaData> createArtifactRevisionIdLocallyAvailableResourceFinder(ArtifactCacheMetaData artifactCacheMetaData, LocalMavenRepositoryLocator localMavenRepositoryLocator, ArtifactIdentifierFileStore fileStore) {
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

    SftpClientFactory createSftpClientFactory() {
        return new SftpClientFactory();
    }

    RepositoryTransportFactory createRepositoryTransportFactory(ProgressLoggerFactory progressLoggerFactory,
                                                                TemporaryFileProvider temporaryFileProvider,
                                                                ByUrlCachedExternalResourceIndex externalResourceIndex,
                                                                BuildCommencedTimeProvider buildCommencedTimeProvider,
                                                                SftpClientFactory sftpClientFactory,
                                                                CacheLockingManager cacheLockingManager) {
        return new RepositoryTransportFactory(
                progressLoggerFactory,
                temporaryFileProvider,
                externalResourceIndex,
                buildCommencedTimeProvider,
                sftpClientFactory,
                cacheLockingManager
        );
    }

    ResolveIvyFactory createResolveIvyFactory(StartParameter startParameter, ModuleVersionsCache moduleVersionsCache, ModuleMetaDataCache moduleMetaDataCache, ModuleArtifactsCache moduleArtifactsCache,
                                              ArtifactAtRepositoryCachedArtifactIndex artifactAtRepositoryCachedArtifactIndex, CacheLockingManager cacheLockingManager,
                                              BuildCommencedTimeProvider buildCommencedTimeProvider, InMemoryCachedRepositoryFactory inMemoryCachedRepositoryFactory,
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
                inMemoryCachedRepositoryFactory,
                versionMatcher,
                latestStrategy);
    }

    ArtifactDependencyResolver createArtifactDependencyResolver(ResolveIvyFactory resolveIvyFactory, LocalComponentFactory publishModuleDescriptorConverter, DependencyDescriptorFactory dependencyDescriptorFactory,
                                                                CacheLockingManager cacheLockingManager, IvyContextManager ivyContextManager, ResolutionResultsStoreFactory resolutionResultsStoreFactory,
                                                                LatestStrategy latestStrategy, ProjectRegistry<ProjectInternal> projectRegistry, ComponentIdentifierFactory componentIdentifierFactory) {
        ArtifactDependencyResolver resolver = new DefaultDependencyResolver(
                resolveIvyFactory,
                publishModuleDescriptorConverter,
                dependencyDescriptorFactory,
                new DefaultProjectComponentRegistry(
                        publishModuleDescriptorConverter,
                        projectRegistry),
                cacheLockingManager,
                ivyContextManager,
                resolutionResultsStoreFactory,
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
