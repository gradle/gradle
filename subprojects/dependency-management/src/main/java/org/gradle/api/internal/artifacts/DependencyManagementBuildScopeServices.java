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
import org.gradle.api.internal.artifacts.component.DefaultBuildIdentifier;
import org.gradle.api.internal.artifacts.component.DefaultComponentIdentifierFactory;
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyFactory;
import org.gradle.api.internal.artifacts.ivyservice.ArtifactCacheMetaData;
import org.gradle.api.internal.artifacts.ivyservice.CacheLockingArtifactDependencyResolver;
import org.gradle.api.internal.artifacts.ivyservice.CacheLockingManager;
import org.gradle.api.internal.artifacts.ivyservice.dynamicversions.ModuleVersionsCache;
import org.gradle.api.internal.artifacts.ivyservice.dynamicversions.SingleFileBackedModuleVersionsCache;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ComponentResolvers;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.DelegatingComponentResolvers;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ResolveIvyFactory;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ResolverProviderFactory;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.StartParameterResolutionOverride;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.memcache.InMemoryCachedRepositoryFactory;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.DefaultVersionComparator;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.DefaultVersionSelectorScheme;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionComparator;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelectorScheme;
import org.gradle.api.internal.artifacts.ivyservice.modulecache.DefaultModuleArtifactsCache;
import org.gradle.api.internal.artifacts.ivyservice.modulecache.DefaultModuleMetaDataCache;
import org.gradle.api.internal.artifacts.ivyservice.modulecache.ModuleArtifactsCache;
import org.gradle.api.internal.artifacts.ivyservice.modulecache.ModuleMetaDataCache;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.ConfigurationComponentMetaDataBuilder;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.DependencyDescriptorFactory;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.AggregatingProjectArtifactBuilder;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.CacheLockReleasingProjectArtifactBuilder;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.DefaultLocalComponentRegistry;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.DefaultProjectLocalComponentProvider;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.DefaultProjectPublicationRegistry;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.LocalComponentProvider;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.LocalComponentRegistry;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectArtifactBuilder;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectDependencyResolver;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectLocalComponentProvider;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectPublicationRegistry;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.DefaultArtifactDependencyResolver;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.store.ResolutionResultsStoreFactory;
import org.gradle.api.internal.artifacts.mvnsettings.DefaultLocalMavenRepositoryLocator;
import org.gradle.api.internal.artifacts.mvnsettings.DefaultMavenFileLocations;
import org.gradle.api.internal.artifacts.mvnsettings.DefaultMavenSettingsProvider;
import org.gradle.api.internal.artifacts.mvnsettings.LocalMavenRepositoryLocator;
import org.gradle.api.internal.artifacts.mvnsettings.MavenSettingsProvider;
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransportFactory;
import org.gradle.api.internal.cache.GeneratedGradleJarCache;
import org.gradle.api.internal.file.FileLookup;
import org.gradle.api.internal.file.TemporaryFileProvider;
import org.gradle.api.internal.file.TmpDirTemporaryFileProvider;
import org.gradle.api.internal.filestore.ivy.ArtifactIdentifierFileStore;
import org.gradle.api.internal.notations.ClientModuleNotationParserFactory;
import org.gradle.api.internal.notations.DependencyNotationParser;
import org.gradle.api.internal.notations.ProjectDependencyFactory;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.ProjectRegistry;
import org.gradle.api.internal.runtimeshaded.RuntimeShadedJarFactory;
import org.gradle.initialization.BuildIdentity;
import org.gradle.initialization.DefaultBuildIdentity;
import org.gradle.initialization.ProjectAccessListener;
import org.gradle.internal.component.external.model.ModuleComponentArtifactMetadata;
import org.gradle.internal.installation.CurrentGradleInstallation;
import org.gradle.internal.logging.progress.ProgressLoggerFactory;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.resource.cached.ByUrlCachedExternalResourceIndex;
import org.gradle.internal.resource.cached.ivy.ArtifactAtRepositoryCachedArtifactIndex;
import org.gradle.internal.resource.connector.ResourceConnectorFactory;
import org.gradle.internal.resource.local.LocallyAvailableResourceFinder;
import org.gradle.internal.resource.local.UniquePathKeyFileStore;
import org.gradle.internal.resource.local.ivy.LocallyAvailableResourceFinderFactory;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.util.BuildCommencedTimeProvider;

import java.util.List;

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

    BuildIdentity createBuildIdentity(ProjectRegistry<ProjectInternal> projectRegistry) {
        ProjectInternal rootProject = projectRegistry.getProject(":");
        if (rootProject == null || rootProject.getGradle().getParent() == null) {
            // BuildIdentity for a top-level build
            return new DefaultBuildIdentity(new DefaultBuildIdentifier(":", true));
        }
        // BuildIdentity for an included build
        // This hard-codes the assumption that buildName == rootProject.name for included builds
        return new DefaultBuildIdentity(new DefaultBuildIdentifier(rootProject.getName(), true));
    }

    ComponentIdentifierFactory createComponentIdentifierFactory(BuildIdentity buildIdentity) {
        return new DefaultComponentIdentifierFactory(buildIdentity);
    }

    DependencyFactory createDependencyFactory(
            Instantiator instantiator,
            ProjectAccessListener projectAccessListener,
            StartParameter startParameter,
            ClassPathRegistry classPathRegistry,
            CurrentGradleInstallation currentGradleInstallation,
            FileLookup fileLookup,
            RuntimeShadedJarFactory runtimeShadedJarFactory
    ) {
        DefaultProjectDependencyFactory factory = new DefaultProjectDependencyFactory(
            projectAccessListener, instantiator, startParameter.isBuildProjectDependencies());

        ProjectDependencyFactory projectDependencyFactory = new ProjectDependencyFactory(factory);

        return new DefaultDependencyFactory(
            DependencyNotationParser.parser(instantiator, factory, classPathRegistry, fileLookup, runtimeShadedJarFactory, currentGradleInstallation),
            new ClientModuleNotationParserFactory(instantiator).create(),
            projectDependencyFactory);
    }

    RuntimeShadedJarFactory createRuntimeShadedJarFactory(GeneratedGradleJarCache jarCache, ProgressLoggerFactory progressLoggerFactory) {
        return new RuntimeShadedJarFactory(jarCache, progressLoggerFactory);
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

    ModuleMetaDataCache createModuleDescriptorCache(BuildCommencedTimeProvider timeProvider, CacheLockingManager cacheLockingManager, ArtifactCacheMetaData artifactCacheMetaData) {
        return new DefaultModuleMetaDataCache(
            timeProvider,
            cacheLockingManager,
            artifactCacheMetaData
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

    ArtifactIdentifierFileStore createArtifactRevisionIdFileStore(ArtifactCacheMetaData artifactCacheMetaData) {
        return new ArtifactIdentifierFileStore(new UniquePathKeyFileStore(artifactCacheMetaData.getFileStoreDirectory()), new TmpDirTemporaryFileProvider());
    }

    MavenSettingsProvider createMavenSettingsProvider() {
        return new DefaultMavenSettingsProvider(new DefaultMavenFileLocations());
    }

    LocalMavenRepositoryLocator createLocalMavenRepositoryLocator(MavenSettingsProvider mavenSettingsProvider) {
        return new DefaultLocalMavenRepositoryLocator(mavenSettingsProvider);
    }

    LocallyAvailableResourceFinder<ModuleComponentArtifactMetadata> createArtifactRevisionIdLocallyAvailableResourceFinder(ArtifactCacheMetaData artifactCacheMetaData, LocalMavenRepositoryLocator localMavenRepositoryLocator, ArtifactIdentifierFileStore fileStore) {
        LocallyAvailableResourceFinderFactory finderFactory = new LocallyAvailableResourceFinderFactory(
            artifactCacheMetaData,
            localMavenRepositoryLocator,
            fileStore);
        return finderFactory.create();
    }

    VersionSelectorScheme createVersionSelectorScheme(VersionComparator versionComparator) {
        return new DefaultVersionSelectorScheme(versionComparator);
    }

    VersionComparator createVersionComparator() {
        return new DefaultVersionComparator();
    }

    RepositoryTransportFactory createRepositoryTransportFactory(ProgressLoggerFactory progressLoggerFactory,
                                                                TemporaryFileProvider temporaryFileProvider,
                                                                ByUrlCachedExternalResourceIndex externalResourceIndex,
                                                                BuildCommencedTimeProvider buildCommencedTimeProvider,
                                                                CacheLockingManager cacheLockingManager,
                                                                ServiceRegistry serviceRegistry) {
        return new RepositoryTransportFactory(
            serviceRegistry.getAll(ResourceConnectorFactory.class),
            progressLoggerFactory,
            temporaryFileProvider,
            externalResourceIndex,
            buildCommencedTimeProvider,
            cacheLockingManager
        );
    }

    ResolveIvyFactory createResolveIvyFactory(StartParameter startParameter, ModuleVersionsCache moduleVersionsCache, ModuleMetaDataCache moduleMetaDataCache, ModuleArtifactsCache moduleArtifactsCache,
                                              ArtifactAtRepositoryCachedArtifactIndex artifactAtRepositoryCachedArtifactIndex, CacheLockingManager cacheLockingManager,
                                              BuildCommencedTimeProvider buildCommencedTimeProvider, InMemoryCachedRepositoryFactory inMemoryCachedRepositoryFactory,
                                              VersionSelectorScheme versionSelectorScheme, VersionComparator versionComparator) {
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
            versionSelectorScheme,
            versionComparator);
    }

    ArtifactDependencyResolver createArtifactDependencyResolver(ResolveIvyFactory resolveIvyFactory,
                                                                DependencyDescriptorFactory dependencyDescriptorFactory,
                                                                CacheLockingManager cacheLockingManager,
                                                                VersionComparator versionComparator,
                                                                ServiceRegistry serviceRegistry) {
        ArtifactDependencyResolver resolver = new DefaultArtifactDependencyResolver(
            serviceRegistry,
            resolveIvyFactory,
            dependencyDescriptorFactory,
            cacheLockingManager,
            versionComparator
        );
        return new CacheLockingArtifactDependencyResolver(cacheLockingManager, resolver);
    }

    ResolutionResultsStoreFactory createResolutionResultsStoreFactory(TemporaryFileProvider temporaryFileProvider) {
        return new ResolutionResultsStoreFactory(temporaryFileProvider);
    }

    ProjectPublicationRegistry createProjectPublicationRegistry() {
        return new DefaultProjectPublicationRegistry();
    }

    ProjectLocalComponentProvider createProjectComponentProvider(ProjectRegistry<ProjectInternal> projectRegistry, ConfigurationComponentMetaDataBuilder metaDataBuilder) {
        return new DefaultProjectLocalComponentProvider(projectRegistry, metaDataBuilder);
    }

    LocalComponentRegistry createLocalComponentRegistry(ServiceRegistry serviceRegistry) {
        List<LocalComponentProvider> providers = serviceRegistry.getAll(LocalComponentProvider.class);
        return new DefaultLocalComponentRegistry(providers);
    }

    ProjectDependencyResolver createProjectDependencyResolver(LocalComponentRegistry localComponentRegistry, ServiceRegistry serviceRegistry,
                                                              CacheLockingManager cacheLockingManager, ComponentIdentifierFactory componentIdentifierFactory) {
        // This doesn't seem to consistently load all ProjectArtifactBuilder instances provided by modules.
        // For embedded integration tests, I'm not convinced that the CompositeProjectArtifactBuilder will always be registered.
        List<ProjectArtifactBuilder> delegateBuilders = serviceRegistry.getAll(ProjectArtifactBuilder.class);
        ProjectArtifactBuilder artifactBuilder = new AggregatingProjectArtifactBuilder(delegateBuilders);
        artifactBuilder = new CacheLockReleasingProjectArtifactBuilder(artifactBuilder, cacheLockingManager);
        return new ProjectDependencyResolver(localComponentRegistry, artifactBuilder, componentIdentifierFactory);
    }

    ResolverProviderFactory createProjectResolverProviderFactory(final ProjectDependencyResolver resolver) {
        return new ProjectResolverProviderFactory(resolver);
    }

    private static class ProjectResolverProviderFactory implements ResolverProviderFactory {
        private final ProjectDependencyResolver resolver;

        public ProjectResolverProviderFactory(ProjectDependencyResolver resolver) {
            this.resolver = resolver;
        }

        @Override
        public boolean canCreate(ResolveContext context) {
            return true;
        }

        @Override
        public ComponentResolvers create(ResolveContext context) {
            return DelegatingComponentResolvers.of(resolver);
        }
    }
}
