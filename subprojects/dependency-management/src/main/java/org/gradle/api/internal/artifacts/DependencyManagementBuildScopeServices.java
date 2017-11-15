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

import com.google.common.collect.Sets;
import org.gradle.StartParameter;
import org.gradle.api.internal.ClassPathRegistry;
import org.gradle.api.internal.ExperimentalFeatures;
import org.gradle.api.internal.artifacts.component.ComponentIdentifierFactory;
import org.gradle.api.internal.artifacts.component.DefaultBuildIdentifier;
import org.gradle.api.internal.artifacts.component.DefaultComponentIdentifierFactory;
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyFactory;
import org.gradle.api.internal.artifacts.ivyservice.ArtifactCacheMetaData;
import org.gradle.api.internal.artifacts.ivyservice.CacheLockingManager;
import org.gradle.api.internal.artifacts.ivyservice.dynamicversions.ModuleVersionsCache;
import org.gradle.api.internal.artifacts.ivyservice.dynamicversions.SingleFileBackedModuleVersionsCache;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ComponentResolvers;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ConnectionFailureRepositoryBlacklister;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.RepositoryBlacklister;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ResolveIvyFactory;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ResolverProviderFactory;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.StartParameterResolutionOverride;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.memcache.InMemoryCachedRepositoryFactory;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionComparator;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelectorScheme;
import org.gradle.api.internal.artifacts.ivyservice.modulecache.DefaultModuleArtifactsCache;
import org.gradle.api.internal.artifacts.ivyservice.modulecache.DefaultModuleMetaDataCache;
import org.gradle.api.internal.artifacts.ivyservice.modulecache.ModuleArtifactsCache;
import org.gradle.api.internal.artifacts.ivyservice.modulecache.ModuleMetaDataCache;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.ConfigurationComponentMetaDataBuilder;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.DependencyDescriptorFactory;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.DefaultLocalComponentRegistry;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.DefaultProjectLocalComponentProvider;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.DefaultProjectPublicationRegistry;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.LocalComponentProvider;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.LocalComponentRegistry;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectDependencyResolver;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectLocalComponentProvider;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectPublicationRegistry;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.DefaultArtifactDependencyResolver;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.ModuleExclusions;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.store.ResolutionResultsStoreFactory;
import org.gradle.api.internal.artifacts.mvnsettings.DefaultLocalMavenRepositoryLocator;
import org.gradle.api.internal.artifacts.mvnsettings.DefaultMavenFileLocations;
import org.gradle.api.internal.artifacts.mvnsettings.DefaultMavenSettingsProvider;
import org.gradle.api.internal.artifacts.mvnsettings.LocalMavenRepositoryLocator;
import org.gradle.api.internal.artifacts.mvnsettings.MavenSettingsProvider;
import org.gradle.api.internal.artifacts.repositories.resolver.DefaultExternalResourceAccessor;
import org.gradle.api.internal.artifacts.repositories.resolver.ExternalResourceAccessor;
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransport;
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransportFactory;
import org.gradle.api.internal.artifacts.vcs.VcsDependencyResolver;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.api.internal.file.FileLookup;
import org.gradle.api.internal.file.TemporaryFileProvider;
import org.gradle.api.internal.file.TmpDirTemporaryFileProvider;
import org.gradle.api.internal.file.collections.DirectoryFileTreeFactory;
import org.gradle.api.internal.filestore.ivy.ArtifactIdentifierFileStore;
import org.gradle.api.internal.model.NamedObjectInstantiator;
import org.gradle.api.internal.notations.ClientModuleNotationParserFactory;
import org.gradle.api.internal.notations.DependencyNotationParser;
import org.gradle.api.internal.notations.ProjectDependencyFactory;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.ProjectRegistry;
import org.gradle.api.internal.runtimeshaded.RuntimeShadedJarFactory;
import org.gradle.authentication.Authentication;
import org.gradle.cache.internal.GeneratedGradleJarCache;
import org.gradle.cache.internal.ProducerGuard;
import org.gradle.initialization.BuildIdentity;
import org.gradle.initialization.DefaultBuildIdentity;
import org.gradle.initialization.ProjectAccessListener;
import org.gradle.initialization.layout.ProjectCacheDir;
import org.gradle.internal.component.external.model.ModuleComponentArtifactMetadata;
import org.gradle.internal.installation.CurrentGradleInstallation;
import org.gradle.internal.logging.progress.ProgressLoggerFactory;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.resource.ExternalResourceName;
import org.gradle.internal.resource.TextResourceLoader;
import org.gradle.internal.resource.cached.ByUrlCachedExternalResourceIndex;
import org.gradle.internal.resource.cached.ExternalResourceFileStore;
import org.gradle.internal.resource.cached.ivy.ArtifactAtRepositoryCachedArtifactIndex;
import org.gradle.internal.resource.connector.ResourceConnectorFactory;
import org.gradle.internal.resource.local.FileResourceRepository;
import org.gradle.internal.resource.local.LocallyAvailableResourceFinder;
import org.gradle.internal.resource.local.ivy.LocallyAvailableResourceFinderFactory;
import org.gradle.internal.resource.transfer.DefaultUriTextResourceLoader;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.util.BuildCommencedTimeProvider;
import org.gradle.vcs.internal.VcsMappingFactory;
import org.gradle.vcs.internal.VcsMappingsInternal;
import org.gradle.vcs.internal.VersionControlSystemFactory;

import java.util.Collections;
import java.util.HashSet;
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
        ProjectInternal rootProject = projectRegistry.getRootProject();
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

    RuntimeShadedJarFactory createRuntimeShadedJarFactory(GeneratedGradleJarCache jarCache, ProgressLoggerFactory progressLoggerFactory, DirectoryFileTreeFactory directoryFileTreeFactory) {
        return new RuntimeShadedJarFactory(jarCache, progressLoggerFactory, directoryFileTreeFactory);
    }

    BuildCommencedTimeProvider createBuildTimeProvider() {
        return new BuildCommencedTimeProvider();
    }

    ModuleExclusions createModuleExclusions(ImmutableModuleIdentifierFactory moduleIdentifierFactory) {
        return new ModuleExclusions(moduleIdentifierFactory);
    }

    ModuleVersionsCache createModuleVersionsCache(BuildCommencedTimeProvider timeProvider, CacheLockingManager cacheLockingManager, ImmutableModuleIdentifierFactory moduleIdentifierFactory) {
        return new SingleFileBackedModuleVersionsCache(
            timeProvider,
            cacheLockingManager,
            moduleIdentifierFactory);
    }

    ModuleArtifactsCache createModuleArtifactsCache(BuildCommencedTimeProvider timeProvider, CacheLockingManager cacheLockingManager) {
        return new DefaultModuleArtifactsCache(
            timeProvider,
            cacheLockingManager
        );
    }

    ModuleMetaDataCache createModuleDescriptorCache(BuildCommencedTimeProvider timeProvider, CacheLockingManager cacheLockingManager, ArtifactCacheMetaData artifactCacheMetaData, ImmutableModuleIdentifierFactory moduleIdentifierFactory, ImmutableAttributesFactory attributesFactory) {
        return new DefaultModuleMetaDataCache(
            timeProvider,
            cacheLockingManager,
            artifactCacheMetaData,
            moduleIdentifierFactory,
            attributesFactory,
            NamedObjectInstantiator.INSTANCE);
    }

    ArtifactAtRepositoryCachedArtifactIndex createArtifactAtRepositoryCachedResolutionIndex(BuildCommencedTimeProvider timeProvider, CacheLockingManager cacheLockingManager) {
        return new ArtifactAtRepositoryCachedArtifactIndex(
            "module-artifact",
            timeProvider,
            cacheLockingManager
        );
    }

    ByUrlCachedExternalResourceIndex createArtifactUrlCachedResolutionIndex(BuildCommencedTimeProvider timeProvider, CacheLockingManager cacheLockingManager) {
        return new ByUrlCachedExternalResourceIndex(
            "resource-at-url",
            timeProvider,
            cacheLockingManager
        );
    }

    ArtifactIdentifierFileStore createArtifactRevisionIdFileStore(ArtifactCacheMetaData artifactCacheMetaData) {
        return new ArtifactIdentifierFileStore(artifactCacheMetaData.getFileStoreDirectory(), new TmpDirTemporaryFileProvider());
    }

    ExternalResourceFileStore createExternalResourceFileStore(ArtifactCacheMetaData artifactCacheMetaData) {
        return new ExternalResourceFileStore(artifactCacheMetaData.getExternalResourcesStoreDirectory(), new TmpDirTemporaryFileProvider());
    }

    TextResourceLoader createTextResourceLoader(ExternalResourceFileStore resourceFileStore, RepositoryTransportFactory repositoryTransportFactory) {
        HashSet<String> schemas = Sets.newHashSet("https", "http");
        RepositoryTransport transport = repositoryTransportFactory.createTransport(schemas, "http auth", Collections.<Authentication>emptyList());
        ExternalResourceAccessor externalResourceAccessor = new DefaultExternalResourceAccessor(resourceFileStore, transport.getResourceAccessor());
        return new DefaultUriTextResourceLoader(externalResourceAccessor, schemas);
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

    RepositoryTransportFactory createRepositoryTransportFactory(StartParameter startParameter,
                                                                ProgressLoggerFactory progressLoggerFactory,
                                                                TemporaryFileProvider temporaryFileProvider,
                                                                ByUrlCachedExternalResourceIndex externalResourceIndex,
                                                                BuildCommencedTimeProvider buildCommencedTimeProvider,
                                                                CacheLockingManager cacheLockingManager,
                                                                List<ResourceConnectorFactory> resourceConnectorFactories,
                                                                BuildOperationExecutor buildOperationExecutor,
                                                                ProducerGuard<ExternalResourceName> producerGuard,
                                                                FileResourceRepository fileResourceRepository) {
        StartParameterResolutionOverride startParameterResolutionOverride = new StartParameterResolutionOverride(startParameter);
        return new RepositoryTransportFactory(
            resourceConnectorFactories,
            progressLoggerFactory,
            temporaryFileProvider,
            externalResourceIndex,
            buildCommencedTimeProvider,
            cacheLockingManager,
            buildOperationExecutor,
            startParameterResolutionOverride,
            producerGuard,
            fileResourceRepository);
    }

    RepositoryBlacklister createRepositoryBlacklister() {
        return new ConnectionFailureRepositoryBlacklister();
    }

    ResolveIvyFactory createResolveIvyFactory(StartParameter startParameter, ModuleVersionsCache moduleVersionsCache, ModuleMetaDataCache moduleMetaDataCache, ModuleArtifactsCache moduleArtifactsCache,
                                              ArtifactAtRepositoryCachedArtifactIndex artifactAtRepositoryCachedArtifactIndex,
                                              BuildCommencedTimeProvider buildCommencedTimeProvider, InMemoryCachedRepositoryFactory inMemoryCachedRepositoryFactory,
                                              VersionSelectorScheme versionSelectorScheme,
                                              VersionComparator versionComparator,
                                              ImmutableModuleIdentifierFactory moduleIdentifierFactory, RepositoryBlacklister repositoryBlacklister) {
        StartParameterResolutionOverride startParameterResolutionOverride = new StartParameterResolutionOverride(startParameter);
        return new ResolveIvyFactory(
            moduleVersionsCache,
            moduleMetaDataCache,
            moduleArtifactsCache,
            artifactAtRepositoryCachedArtifactIndex,
            startParameterResolutionOverride,
            buildCommencedTimeProvider,
            inMemoryCachedRepositoryFactory,
            versionSelectorScheme,
            versionComparator,
            moduleIdentifierFactory,
            repositoryBlacklister);
    }

    ArtifactDependencyResolver createArtifactDependencyResolver(ResolveIvyFactory resolveIvyFactory,
                                                                DependencyDescriptorFactory dependencyDescriptorFactory,
                                                                VersionComparator versionComparator,
                                                                List<ResolverProviderFactory> resolverFactories,
                                                                ModuleExclusions moduleExclusions,
                                                                BuildOperationExecutor buildOperationExecutor,
                                                                ComponentSelectorConverter componentSelectorConverter,
                                                                ExperimentalFeatures experimentalFeatures) {
        return new DefaultArtifactDependencyResolver(
            buildOperationExecutor,
            resolverFactories,
            resolveIvyFactory,
            dependencyDescriptorFactory,
            versionComparator,
            moduleExclusions,
            componentSelectorConverter,
            experimentalFeatures);
    }

    ResolutionResultsStoreFactory createResolutionResultsStoreFactory(TemporaryFileProvider temporaryFileProvider) {
        return new ResolutionResultsStoreFactory(temporaryFileProvider);
    }

    ProjectPublicationRegistry createProjectPublicationRegistry() {
        return new DefaultProjectPublicationRegistry();
    }

    ProjectLocalComponentProvider createProjectComponentProvider(ProjectRegistry<ProjectInternal> projectRegistry, ConfigurationComponentMetaDataBuilder metaDataBuilder, ImmutableModuleIdentifierFactory moduleIdentifierFactory) {
        return new DefaultProjectLocalComponentProvider(projectRegistry, metaDataBuilder, moduleIdentifierFactory);
    }

    LocalComponentRegistry createLocalComponentRegistry(List<LocalComponentProvider> providers) {
        return new DefaultLocalComponentRegistry(providers);
    }

    ProjectDependencyResolver createProjectDependencyResolver(LocalComponentRegistry localComponentRegistry, ComponentIdentifierFactory componentIdentifierFactory) {
        return new ProjectDependencyResolver(localComponentRegistry, componentIdentifierFactory);
    }

    ComponentSelectorConverter createModuleVersionSelectorFactory(ImmutableModuleIdentifierFactory moduleIdentifierFactory, ComponentIdentifierFactory componentIdentifierFactory, LocalComponentRegistry localComponentRegistry) {
        return new DefaultComponentSelectorConverter(moduleIdentifierFactory, componentIdentifierFactory, localComponentRegistry);
    }

    private static class VcsOrProjectResolverProviderFactory implements ResolverProviderFactory {
        private final VcsDependencyResolver vcsDependencyResolver;
        private final ProjectDependencyResolver projectDependencyResolver;
        private final VcsMappingsInternal vcsMappingsInternal;

        private VcsOrProjectResolverProviderFactory(VcsDependencyResolver vcsDependencyResolver, ProjectDependencyResolver projectDependencyResolver, VcsMappingsInternal vcsMappingsInternal) {
            this.vcsDependencyResolver = vcsDependencyResolver;
            this.projectDependencyResolver = projectDependencyResolver;
            this.vcsMappingsInternal = vcsMappingsInternal;
        }

        @Override
        public boolean canCreate(ResolveContext context) {
            return true;
        }

        @Override
        public ComponentResolvers create(ResolveContext context) {
            return vcsMappingsInternal.hasRules() ? vcsDependencyResolver : projectDependencyResolver;
        }
    }

    VcsDependencyResolver createVcsDependencyResolver(ServiceRegistry serviceRegistry, ProjectCacheDir projectCacheDir, ProjectDependencyResolver projectDependencyResolver, LocalComponentRegistry localComponentRegistry, ProjectRegistry<ProjectInternal> projectRegistry, VcsMappingsInternal vcsMappingsInternal, VcsMappingFactory vcsMappingFactory, VersionControlSystemFactory versionControlSystemFactory) {
        return new VcsDependencyResolver(projectCacheDir.getDir(), projectDependencyResolver, serviceRegistry, localComponentRegistry, vcsMappingsInternal, vcsMappingFactory, versionControlSystemFactory);
    }

    ResolverProviderFactory createVcsResolverProviderFactory(VcsDependencyResolver vcsDependencyResolver, ProjectDependencyResolver projectDependencyResolver, VcsMappingsInternal vcsMappingsInternal) {
        return new VcsOrProjectResolverProviderFactory(vcsDependencyResolver, projectDependencyResolver, vcsMappingsInternal);
    }
}
