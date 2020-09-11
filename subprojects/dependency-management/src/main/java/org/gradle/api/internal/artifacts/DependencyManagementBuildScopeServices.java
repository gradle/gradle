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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import org.gradle.BuildAdapter;
import org.gradle.StartParameter;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.initialization.Settings;
import org.gradle.api.internal.ClassPathRegistry;
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.api.internal.FeaturePreviews;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.artifacts.component.ComponentIdentifierFactory;
import org.gradle.api.internal.artifacts.component.DefaultComponentIdentifierFactory;
import org.gradle.api.internal.artifacts.dsl.CapabilityNotationParserFactory;
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyFactory;
import org.gradle.api.internal.artifacts.ivyservice.ArtifactCacheLockingManager;
import org.gradle.api.internal.artifacts.ivyservice.ArtifactCacheMetadata;
import org.gradle.api.internal.artifacts.ivyservice.ArtifactCachesProvider;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ChangingValueDependencyResolutionListener;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ConnectionFailureRepositoryDisabler;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleDescriptorHashCodec;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleDescriptorHashModuleSource;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.RepositoryDisabler;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ResolveIvyFactory;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ResolverProviderFactory;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.StartParameterResolutionOverride;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.CachingVersionSelectorScheme;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.DefaultVersionComparator;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.DefaultVersionSelectorScheme;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionComparator;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionParser;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelectorScheme;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.verification.DependencyVerificationOverride;
import org.gradle.api.internal.artifacts.ivyservice.modulecache.AbstractModuleMetadataCache;
import org.gradle.api.internal.artifacts.ivyservice.modulecache.FileStoreAndIndexProvider;
import org.gradle.api.internal.artifacts.ivyservice.modulecache.InMemoryModuleMetadataCache;
import org.gradle.api.internal.artifacts.ivyservice.modulecache.ModuleComponentResolveMetadataSerializer;
import org.gradle.api.internal.artifacts.ivyservice.modulecache.ModuleMetadataSerializer;
import org.gradle.api.internal.artifacts.ivyservice.modulecache.ModuleRepositoryCacheProvider;
import org.gradle.api.internal.artifacts.ivyservice.modulecache.ModuleRepositoryCaches;
import org.gradle.api.internal.artifacts.ivyservice.modulecache.ModuleSourcesSerializer;
import org.gradle.api.internal.artifacts.ivyservice.modulecache.PersistentModuleMetadataCache;
import org.gradle.api.internal.artifacts.ivyservice.modulecache.ReadOnlyModuleMetadataCache;
import org.gradle.api.internal.artifacts.ivyservice.modulecache.SuppliedComponentMetadataSerializer;
import org.gradle.api.internal.artifacts.ivyservice.modulecache.TwoStageModuleMetadataCache;
import org.gradle.api.internal.artifacts.ivyservice.modulecache.artifacts.AbstractArtifactsCache;
import org.gradle.api.internal.artifacts.ivyservice.modulecache.artifacts.DefaultModuleArtifactCache;
import org.gradle.api.internal.artifacts.ivyservice.modulecache.artifacts.DefaultModuleArtifactsCache;
import org.gradle.api.internal.artifacts.ivyservice.modulecache.artifacts.InMemoryModuleArtifactCache;
import org.gradle.api.internal.artifacts.ivyservice.modulecache.artifacts.InMemoryModuleArtifactsCache;
import org.gradle.api.internal.artifacts.ivyservice.modulecache.artifacts.ModuleArtifactCache;
import org.gradle.api.internal.artifacts.ivyservice.modulecache.artifacts.ReadOnlyModuleArtifactCache;
import org.gradle.api.internal.artifacts.ivyservice.modulecache.artifacts.ReadOnlyModuleArtifactsCache;
import org.gradle.api.internal.artifacts.ivyservice.modulecache.artifacts.TwoStageArtifactsCache;
import org.gradle.api.internal.artifacts.ivyservice.modulecache.artifacts.TwoStageModuleArtifactCache;
import org.gradle.api.internal.artifacts.ivyservice.modulecache.dynamicversions.AbstractModuleVersionsCache;
import org.gradle.api.internal.artifacts.ivyservice.modulecache.dynamicversions.DefaultModuleVersionsCache;
import org.gradle.api.internal.artifacts.ivyservice.modulecache.dynamicversions.InMemoryModuleVersionsCache;
import org.gradle.api.internal.artifacts.ivyservice.modulecache.dynamicversions.ReadOnlyModuleVersionsCache;
import org.gradle.api.internal.artifacts.ivyservice.modulecache.dynamicversions.TwoStageModuleVersionsCache;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.LocalComponentMetadataBuilder;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.DependencyDescriptorFactory;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.DefaultLocalComponentRegistry;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.DefaultProjectLocalComponentProvider;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.DefaultProjectPublicationRegistry;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.LocalComponentProvider;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.LocalComponentRegistry;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectArtifactResolver;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectArtifactSetResolver;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectDependencyResolver;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectPublicationRegistry;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.DefaultArtifactDependencyResolver;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.ModuleExclusions;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.AttributeContainerSerializer;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.CachingComponentSelectionDescriptorFactory;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionDescriptorFactory;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.DesugaredAttributeContainerSerializer;
import org.gradle.api.internal.artifacts.mvnsettings.DefaultLocalMavenRepositoryLocator;
import org.gradle.api.internal.artifacts.mvnsettings.DefaultMavenFileLocations;
import org.gradle.api.internal.artifacts.mvnsettings.DefaultMavenSettingsProvider;
import org.gradle.api.internal.artifacts.mvnsettings.LocalMavenRepositoryLocator;
import org.gradle.api.internal.artifacts.mvnsettings.MavenSettingsProvider;
import org.gradle.api.internal.artifacts.repositories.metadata.DefaultMetadataFileSourceCodec;
import org.gradle.api.internal.artifacts.repositories.metadata.IvyMutableModuleMetadataFactory;
import org.gradle.api.internal.artifacts.repositories.metadata.MavenMutableModuleMetadataFactory;
import org.gradle.api.internal.artifacts.repositories.metadata.MetadataFileSource;
import org.gradle.api.internal.artifacts.repositories.resolver.DefaultExternalResourceAccessor;
import org.gradle.api.internal.artifacts.repositories.resolver.ExternalResourceAccessor;
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransport;
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransportFactory;
import org.gradle.api.internal.artifacts.verification.signatures.DefaultSignatureVerificationServiceFactory;
import org.gradle.api.internal.artifacts.verification.signatures.SignatureVerificationServiceFactory;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.file.TemporaryFileProvider;
import org.gradle.api.internal.file.TmpDirTemporaryFileProvider;
import org.gradle.api.internal.filestore.ArtifactIdentifierFileStore;
import org.gradle.api.internal.filestore.DefaultArtifactIdentifierFileStore;
import org.gradle.api.internal.filestore.TwoStageArtifactIdentifierFileStore;
import org.gradle.api.internal.model.NamedObjectInstantiator;
import org.gradle.api.internal.notations.ClientModuleNotationParserFactory;
import org.gradle.api.internal.notations.DependencyConstraintNotationParser;
import org.gradle.api.internal.notations.DependencyNotationParser;
import org.gradle.api.internal.notations.ProjectDependencyFactory;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.ProjectRegistry;
import org.gradle.api.internal.project.ProjectStateRegistry;
import org.gradle.api.internal.properties.GradleProperties;
import org.gradle.api.internal.resources.ApiTextResourceAdapter;
import org.gradle.api.internal.runtimeshaded.RuntimeShadedJarFactory;
import org.gradle.cache.CacheRepository;
import org.gradle.cache.internal.CacheScopeMapping;
import org.gradle.cache.internal.CleaningInMemoryCacheDecoratorFactory;
import org.gradle.cache.internal.GeneratedGradleJarCache;
import org.gradle.cache.internal.InMemoryCacheDecoratorFactory;
import org.gradle.cache.internal.ProducerGuard;
import org.gradle.initialization.InternalBuildFinishedListener;
import org.gradle.initialization.ProjectAccessListener;
import org.gradle.initialization.layout.BuildLayout;
import org.gradle.initialization.layout.ProjectCacheDir;
import org.gradle.internal.build.BuildState;
import org.gradle.internal.build.BuildStateRegistry;
import org.gradle.internal.classpath.ClasspathBuilder;
import org.gradle.internal.classpath.ClasspathWalker;
import org.gradle.internal.component.external.model.ModuleComponentArtifactMetadata;
import org.gradle.internal.component.external.model.PreferJavaRuntimeVariant;
import org.gradle.internal.component.model.PersistentModuleSource;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.file.FileAccessTimeJournal;
import org.gradle.internal.file.RelativeFilePathResolver;
import org.gradle.internal.hash.ChecksumService;
import org.gradle.internal.hash.FileHasher;
import org.gradle.internal.installation.CurrentGradleInstallation;
import org.gradle.internal.instantiation.InstantiatorFactory;
import org.gradle.internal.logging.progress.ProgressLoggerFactory;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.resolve.caching.ComponentMetadataRuleExecutor;
import org.gradle.internal.resolve.caching.ComponentMetadataSupplierRuleExecutor;
import org.gradle.internal.resolve.caching.DesugaringAttributeContainerSerializer;
import org.gradle.internal.resource.ExternalResourceName;
import org.gradle.internal.resource.TextUriResourceLoader;
import org.gradle.internal.resource.cached.ByUrlCachedExternalResourceIndex;
import org.gradle.internal.resource.cached.CachedExternalResourceIndex;
import org.gradle.internal.resource.cached.DefaultExternalResourceFileStore;
import org.gradle.internal.resource.cached.ExternalResourceFileStore;
import org.gradle.internal.resource.cached.TwoStageByUrlCachedExternalResourceIndex;
import org.gradle.internal.resource.cached.TwoStageExternalResourceFileStore;
import org.gradle.internal.resource.connector.ResourceConnectorFactory;
import org.gradle.internal.resource.local.FileResourceListener;
import org.gradle.internal.resource.local.FileResourceRepository;
import org.gradle.internal.resource.local.LocallyAvailableResourceFinder;
import org.gradle.internal.resource.local.ivy.LocallyAvailableResourceFinderFactory;
import org.gradle.internal.resource.transfer.CachingTextUriResourceLoader;
import org.gradle.internal.resource.transport.http.HttpConnectorFactory;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.snapshot.ValueSnapshotter;
import org.gradle.internal.typeconversion.NotationParser;
import org.gradle.util.BuildCommencedTimeProvider;
import org.gradle.util.internal.SimpleMapInterner;

import java.io.File;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The set of dependency management services that are created per build.
 */
class DependencyManagementBuildScopeServices {
    void configure(ServiceRegistration registration) {
        registration.add(ProjectArtifactResolver.class);
        registration.add(ProjectArtifactSetResolver.class);
        registration.add(ProjectDependencyResolver.class);
    }

    DependencyManagementServices createDependencyManagementServices(ServiceRegistry parent) {
        return new DefaultDependencyManagementServices(parent);
    }

    ComponentIdentifierFactory createComponentIdentifierFactory(BuildState currentBuild, BuildStateRegistry buildRegistry) {
        return new DefaultComponentIdentifierFactory(buildRegistry.getBuild(currentBuild.getBuildIdentifier()));
    }

    VersionComparator createVersionComparator(FeaturePreviews featurePreviews, ListenerManager listenerManager) {
        DefaultVersionComparator defaultVersionComparator = new DefaultVersionComparator(featurePreviews);
        // This needs to be removed once the feature preview disappears
        listenerManager.addListener(new BuildAdapter() {
            @Override
            public void settingsEvaluated(Settings settings) {
                defaultVersionComparator.configure();
            }
        });
        return defaultVersionComparator;
    }

    DependencyFactory createDependencyFactory(
        Instantiator instantiator,
        ListenerManager listenerManager,
        StartParameter startParameter,
        ClassPathRegistry classPathRegistry,
        CurrentGradleInstallation currentGradleInstallation,
        FileCollectionFactory fileCollectionFactory,
        RuntimeShadedJarFactory runtimeShadedJarFactory,
        ProjectAccessListener projectAccessListener,
        ImmutableAttributesFactory attributesFactory,
        SimpleMapInterner stringInterner) {
        NotationParser<Object, Capability> capabilityNotationParser = new CapabilityNotationParserFactory(false).create();
        DefaultProjectDependencyFactory factory = new DefaultProjectDependencyFactory(
            projectAccessListener, instantiator, startParameter.isBuildProjectDependencies(), capabilityNotationParser, attributesFactory);
        ProjectDependencyFactory projectDependencyFactory = new ProjectDependencyFactory(factory);

        return new DefaultDependencyFactory(
            DependencyNotationParser.parser(instantiator, factory, classPathRegistry, fileCollectionFactory, runtimeShadedJarFactory, currentGradleInstallation, stringInterner),
            DependencyConstraintNotationParser.parser(instantiator, factory, stringInterner),
            new ClientModuleNotationParserFactory(instantiator, stringInterner).create(),
            capabilityNotationParser, projectDependencyFactory,
            attributesFactory);
    }

    RuntimeShadedJarFactory createRuntimeShadedJarFactory(GeneratedGradleJarCache jarCache, ProgressLoggerFactory progressLoggerFactory, ClasspathWalker classpathWalker, ClasspathBuilder classpathBuilder, BuildOperationExecutor executor) {
        return new RuntimeShadedJarFactory(jarCache, progressLoggerFactory, classpathWalker, classpathBuilder, executor);
    }

    ModuleExclusions createModuleExclusions() {
        return new ModuleExclusions();
    }

    MavenMutableModuleMetadataFactory createMutableMavenMetadataFactory(ImmutableModuleIdentifierFactory moduleIdentifierFactory,
                                                                        ImmutableAttributesFactory attributesFactory,
                                                                        NamedObjectInstantiator instantiator,
                                                                        PreferJavaRuntimeVariant schema) {
        return new MavenMutableModuleMetadataFactory(moduleIdentifierFactory, attributesFactory, instantiator, schema);
    }

    IvyMutableModuleMetadataFactory createMutableIvyMetadataFactory(ImmutableModuleIdentifierFactory moduleIdentifierFactory, ImmutableAttributesFactory attributesFactory, PreferJavaRuntimeVariant schema) {
        return new IvyMutableModuleMetadataFactory(moduleIdentifierFactory, attributesFactory, schema);
    }

    AttributeContainerSerializer createAttributeContainerSerializer(ImmutableAttributesFactory attributesFactory, NamedObjectInstantiator instantiator) {
        return new DesugaredAttributeContainerSerializer(attributesFactory, instantiator);
    }

    ModuleSourcesSerializer createModuleSourcesSerializer(ImmutableModuleIdentifierFactory moduleIdentifierFactory, FileStoreAndIndexProvider fileStoreAndIndexProvider) {
        Map<Integer, PersistentModuleSource.Codec<? extends PersistentModuleSource>> codecs = ImmutableMap.of(
            MetadataFileSource.CODEC_ID, new DefaultMetadataFileSourceCodec(moduleIdentifierFactory, fileStoreAndIndexProvider.getArtifactIdentifierFileStore()),
            ModuleDescriptorHashModuleSource.CODEC_ID, new ModuleDescriptorHashCodec()
        );
        return new ModuleSourcesSerializer(codecs);
    }

    ModuleRepositoryCacheProvider createModuleRepositoryCacheProvider(BuildCommencedTimeProvider timeProvider,
                                                                      ImmutableModuleIdentifierFactory moduleIdentifierFactory,
                                                                      ArtifactCachesProvider artifactCaches,
                                                                      AttributeContainerSerializer attributeContainerSerializer,
                                                                      MavenMutableModuleMetadataFactory mavenMetadataFactory,
                                                                      IvyMutableModuleMetadataFactory ivyMetadataFactory,
                                                                      SimpleMapInterner stringInterner,
                                                                      FileStoreAndIndexProvider fileStoreAndIndexProvider,
                                                                      ModuleSourcesSerializer moduleSourcesSerializer,
                                                                      ChecksumService checksumService) {
        ArtifactIdentifierFileStore artifactIdentifierFileStore = fileStoreAndIndexProvider.getArtifactIdentifierFileStore();
        ModuleRepositoryCaches writableCaches = artifactCaches.withWritableCache((md, manager) -> prepareModuleRepositoryCaches(md, manager, timeProvider, moduleIdentifierFactory, attributeContainerSerializer, mavenMetadataFactory, ivyMetadataFactory, stringInterner, artifactIdentifierFileStore, moduleSourcesSerializer, checksumService));
        AtomicReference<Path> roCachePath = new AtomicReference<>();
        Optional<ModuleRepositoryCaches> readOnlyCaches = artifactCaches.withReadOnlyCache((ro, manager) -> {
            roCachePath.set(ro.getCacheDir().toPath());
            return prepareReadOnlyModuleRepositoryCaches(ro, manager, timeProvider, moduleIdentifierFactory, attributeContainerSerializer, mavenMetadataFactory, ivyMetadataFactory, stringInterner, artifactIdentifierFileStore, moduleSourcesSerializer, checksumService);
        });
        AbstractModuleVersionsCache moduleVersionsCache = readOnlyCaches.map(mrc -> (AbstractModuleVersionsCache) new TwoStageModuleVersionsCache(timeProvider, mrc.moduleVersionsCache, writableCaches.moduleVersionsCache)).orElse(writableCaches.moduleVersionsCache);
        AbstractModuleMetadataCache persistentModuleMetadataCache = readOnlyCaches.map(mrc -> (AbstractModuleMetadataCache) new TwoStageModuleMetadataCache(timeProvider, mrc.moduleMetadataCache, writableCaches.moduleMetadataCache)).orElse(writableCaches.moduleMetadataCache);
        AbstractArtifactsCache moduleArtifactsCache = readOnlyCaches.map(mrc -> (AbstractArtifactsCache) new TwoStageArtifactsCache(timeProvider, mrc.moduleArtifactsCache, writableCaches.moduleArtifactsCache)).orElse(writableCaches.moduleArtifactsCache);
        ModuleArtifactCache moduleArtifactCache = readOnlyCaches.map(mrc -> (ModuleArtifactCache) new TwoStageModuleArtifactCache(roCachePath.get(), mrc.moduleArtifactCache, writableCaches.moduleArtifactCache)).orElse(writableCaches.moduleArtifactCache);
        ModuleRepositoryCaches persistentCaches = new ModuleRepositoryCaches(
            new InMemoryModuleVersionsCache(timeProvider, moduleVersionsCache),
            new InMemoryModuleMetadataCache(timeProvider, persistentModuleMetadataCache),
            new InMemoryModuleArtifactsCache(timeProvider, moduleArtifactsCache),
            new InMemoryModuleArtifactCache(timeProvider, moduleArtifactCache)
        );
        ModuleRepositoryCaches inMemoryOnlyCaches = new ModuleRepositoryCaches(
            new InMemoryModuleVersionsCache(timeProvider),
            new InMemoryModuleMetadataCache(timeProvider),
            new InMemoryModuleArtifactsCache(timeProvider),
            new InMemoryModuleArtifactCache(timeProvider)
        );
        return new ModuleRepositoryCacheProvider(persistentCaches, inMemoryOnlyCaches);
    }

    private ModuleRepositoryCaches prepareModuleRepositoryCaches(ArtifactCacheMetadata artifactCacheMetadata, ArtifactCacheLockingManager artifactCacheLockingManager, BuildCommencedTimeProvider timeProvider, ImmutableModuleIdentifierFactory moduleIdentifierFactory, AttributeContainerSerializer attributeContainerSerializer, MavenMutableModuleMetadataFactory mavenMetadataFactory, IvyMutableModuleMetadataFactory ivyMetadataFactory, SimpleMapInterner stringInterner, ArtifactIdentifierFileStore artifactIdentifierFileStore, ModuleSourcesSerializer moduleSourcesSerializer, ChecksumService checksumService) {
        DefaultModuleVersionsCache moduleVersionsCache = new DefaultModuleVersionsCache(
            timeProvider,
            artifactCacheLockingManager,
            moduleIdentifierFactory);
        PersistentModuleMetadataCache moduleMetadataCache = new PersistentModuleMetadataCache(
            timeProvider,
            artifactCacheLockingManager,
            artifactCacheMetadata,
            moduleIdentifierFactory,
            attributeContainerSerializer,
            mavenMetadataFactory,
            ivyMetadataFactory,
            stringInterner,
            moduleSourcesSerializer,
            checksumService);
        DefaultModuleArtifactsCache moduleArtifactsCache = new DefaultModuleArtifactsCache(
            timeProvider,
            artifactCacheLockingManager
        );
        DefaultModuleArtifactCache moduleArtifactCache = new DefaultModuleArtifactCache(
            "module-artifact",
            timeProvider,
            artifactCacheLockingManager,
            artifactIdentifierFileStore.getFileAccessTracker(),
            artifactCacheMetadata.getCacheDir().toPath()
        );
        return new ModuleRepositoryCaches(
            moduleVersionsCache,
            moduleMetadataCache,
            moduleArtifactsCache,
            moduleArtifactCache
        );
    }

    private ModuleRepositoryCaches prepareReadOnlyModuleRepositoryCaches(ArtifactCacheMetadata artifactCacheMetadata, ArtifactCacheLockingManager artifactCacheLockingManager, BuildCommencedTimeProvider timeProvider, ImmutableModuleIdentifierFactory moduleIdentifierFactory, AttributeContainerSerializer attributeContainerSerializer, MavenMutableModuleMetadataFactory mavenMetadataFactory, IvyMutableModuleMetadataFactory ivyMetadataFactory, SimpleMapInterner stringInterner, ArtifactIdentifierFileStore artifactIdentifierFileStore, ModuleSourcesSerializer moduleSourcesSerializer, ChecksumService checksumService) {
        ReadOnlyModuleVersionsCache moduleVersionsCache = new ReadOnlyModuleVersionsCache(
            timeProvider,
            artifactCacheLockingManager,
            moduleIdentifierFactory);
        ReadOnlyModuleMetadataCache moduleMetadataCache = new ReadOnlyModuleMetadataCache(
            timeProvider,
            artifactCacheLockingManager,
            artifactCacheMetadata,
            moduleIdentifierFactory,
            attributeContainerSerializer,
            mavenMetadataFactory,
            ivyMetadataFactory,
            stringInterner,
            moduleSourcesSerializer,
            checksumService);
        ReadOnlyModuleArtifactsCache moduleArtifactsCache = new ReadOnlyModuleArtifactsCache(
            timeProvider,
            artifactCacheLockingManager
        );
        ReadOnlyModuleArtifactCache moduleArtifactCache = new ReadOnlyModuleArtifactCache(
            "module-artifact",
            timeProvider,
            artifactCacheLockingManager,
            artifactIdentifierFileStore.getFileAccessTracker(),
            artifactCacheMetadata.getCacheDir().toPath()
        );
        return new ModuleRepositoryCaches(
            moduleVersionsCache,
            moduleMetadataCache,
            moduleArtifactsCache,
            moduleArtifactCache
        );
    }

    FileStoreAndIndexProvider createFileStoreAndIndexProvider(BuildCommencedTimeProvider timeProvider, ArtifactCachesProvider artifactCaches, FileAccessTimeJournal fileAccessTimeJournal, ChecksumService checksumService) {
        ExternalResourceFileStore writableFileStore = prepareExternalResourceFileStore(artifactCaches.getWritableCacheMetadata(), fileAccessTimeJournal, checksumService);
        ExternalResourceFileStore externalResourceFileStore = artifactCaches.withReadOnlyCache((md, manager) ->
            (ExternalResourceFileStore) new TwoStageExternalResourceFileStore(prepareExternalResourceFileStore(md, fileAccessTimeJournal, checksumService), writableFileStore)).orElse(writableFileStore);
        CachedExternalResourceIndex<String> writableByUrlCachedExternalResourceIndex = prepareArtifactUrlCachedResolutionIndex(timeProvider, artifactCaches.getWritableCacheLockingManager(), externalResourceFileStore, artifactCaches.getWritableCacheMetadata());
        ArtifactIdentifierFileStore writableArtifactIdentifierFileStore = artifactCaches.withWritableCache((md, manager) -> prepareArtifactRevisionIdFileStore(md, fileAccessTimeJournal, checksumService));
        ArtifactIdentifierFileStore artifactIdentifierFileStore = artifactCaches.withReadOnlyCache((md, manager) -> (ArtifactIdentifierFileStore) new TwoStageArtifactIdentifierFileStore(
            prepareArtifactRevisionIdFileStore(md, fileAccessTimeJournal, checksumService),
            writableArtifactIdentifierFileStore
        )).orElse(writableArtifactIdentifierFileStore);
        return new FileStoreAndIndexProvider(
            artifactCaches.withReadOnlyCache((md, manager) -> (CachedExternalResourceIndex<String>) new TwoStageByUrlCachedExternalResourceIndex(md.getCacheDir().toPath(), prepareArtifactUrlCachedResolutionIndex(timeProvider, manager, externalResourceFileStore, md), writableByUrlCachedExternalResourceIndex)).orElse(writableByUrlCachedExternalResourceIndex),
            externalResourceFileStore, artifactIdentifierFileStore);
    }

    private ByUrlCachedExternalResourceIndex prepareArtifactUrlCachedResolutionIndex(BuildCommencedTimeProvider timeProvider, ArtifactCacheLockingManager artifactCacheLockingManager, ExternalResourceFileStore externalResourceFileStore, ArtifactCacheMetadata artifactCacheMetadata) {
        return new ByUrlCachedExternalResourceIndex(
            "resource-at-url",
            timeProvider,
            artifactCacheLockingManager,
            externalResourceFileStore.getFileAccessTracker(),
            artifactCacheMetadata.getCacheDir().toPath()
        );
    }

    private ArtifactIdentifierFileStore prepareArtifactRevisionIdFileStore(ArtifactCacheMetadata artifactCacheMetadata, FileAccessTimeJournal fileAccessTimeJournal, ChecksumService checksumService) {
        return new DefaultArtifactIdentifierFileStore(artifactCacheMetadata.getFileStoreDirectory(), new TmpDirTemporaryFileProvider(), fileAccessTimeJournal, checksumService);
    }

    private ExternalResourceFileStore prepareExternalResourceFileStore(ArtifactCacheMetadata artifactCacheMetadata, FileAccessTimeJournal fileAccessTimeJournal, ChecksumService checksumService) {
        return new DefaultExternalResourceFileStore(artifactCacheMetadata.getExternalResourcesStoreDirectory(), new TmpDirTemporaryFileProvider(), fileAccessTimeJournal, checksumService);
    }

    TextUriResourceLoader.Factory createTextUrlResourceLoaderFactory(FileStoreAndIndexProvider fileStoreAndIndexProvider, RepositoryTransportFactory repositoryTransportFactory, RelativeFilePathResolver resolver) {
        final HashSet<String> schemas = Sets.newHashSet("https", "http");
        return redirectVerifier -> {
            RepositoryTransport transport = repositoryTransportFactory.createTransport(schemas, "resources http", Collections.emptyList(), redirectVerifier);
            ExternalResourceAccessor externalResourceAccessor = new DefaultExternalResourceAccessor(fileStoreAndIndexProvider.getExternalResourceFileStore(), transport.getResourceAccessor());
            return new CachingTextUriResourceLoader(externalResourceAccessor, schemas, resolver);
        };
    }

    protected ApiTextResourceAdapter.Factory createTextResourceAdapterFactory(TextUriResourceLoader.Factory textUriResourceLoaderFactory, TemporaryFileProvider tempFileProvider) {
        return new ApiTextResourceAdapter.Factory(textUriResourceLoaderFactory, tempFileProvider);
    }

    MavenSettingsProvider createMavenSettingsProvider() {
        return new DefaultMavenSettingsProvider(new DefaultMavenFileLocations());
    }

    LocalMavenRepositoryLocator createLocalMavenRepositoryLocator(MavenSettingsProvider mavenSettingsProvider) {
        return new DefaultLocalMavenRepositoryLocator(mavenSettingsProvider);
    }

    LocallyAvailableResourceFinder<ModuleComponentArtifactMetadata> createArtifactRevisionIdLocallyAvailableResourceFinder(ArtifactCachesProvider artifactCaches,
                                                                                                                           LocalMavenRepositoryLocator localMavenRepositoryLocator,
                                                                                                                           FileStoreAndIndexProvider fileStoreAndIndexProvider,
                                                                                                                           ChecksumService checksumService) {
        LocallyAvailableResourceFinderFactory finderFactory = new LocallyAvailableResourceFinderFactory(
            artifactCaches,
            localMavenRepositoryLocator,
            fileStoreAndIndexProvider.getArtifactIdentifierFileStore(), checksumService);
        return finderFactory.create();
    }

    RepositoryTransportFactory createRepositoryTransportFactory(ProgressLoggerFactory progressLoggerFactory,
                                                                TemporaryFileProvider temporaryFileProvider,
                                                                FileStoreAndIndexProvider fileStoreAndIndexProvider,
                                                                BuildCommencedTimeProvider buildCommencedTimeProvider,
                                                                ArtifactCachesProvider artifactCachesProvider,
                                                                List<ResourceConnectorFactory> resourceConnectorFactories,
                                                                BuildOperationExecutor buildOperationExecutor,
                                                                ProducerGuard<ExternalResourceName> producerGuard,
                                                                FileResourceRepository fileResourceRepository,
                                                                ChecksumService checksumService,
                                                                StartParameterResolutionOverride startParameterResolutionOverride,
                                                                ListenerManager listenerManager) {
        return artifactCachesProvider.withWritableCache((md, manager) -> new RepositoryTransportFactory(
            resourceConnectorFactories,
            progressLoggerFactory,
            temporaryFileProvider,
            fileStoreAndIndexProvider.getExternalResourceIndex(),
            buildCommencedTimeProvider,
            manager,
            buildOperationExecutor,
            startParameterResolutionOverride,
            producerGuard,
            fileResourceRepository,
            checksumService,
            listenerManager.getBroadcaster(FileResourceListener.class)));
    }

    RepositoryDisabler createRepositoryDisabler() {
        return new ConnectionFailureRepositoryDisabler();
    }

    StartParameterResolutionOverride createStartParameterResolutionOverride(StartParameter startParameter, BuildLayout buildLayout) {
        File rootDirectory = buildLayout.getRootDirectory();
        File gradleDir = new File(rootDirectory, "gradle");
        return new StartParameterResolutionOverride(startParameter, gradleDir);
    }

    DependencyVerificationOverride createDependencyVerificationOverride(StartParameterResolutionOverride startParameterResolutionOverride,
                                                                        BuildOperationExecutor buildOperationExecutor,
                                                                        ChecksumService checksumService,
                                                                        SignatureVerificationServiceFactory signatureVerificationServiceFactory,
                                                                        DocumentationRegistry documentationRegistry,
                                                                        ListenerManager listenerManager,
                                                                        BuildCommencedTimeProvider timeProvider,
                                                                        ServiceRegistry serviceRegistry) {
        DependencyVerificationOverride override = startParameterResolutionOverride.dependencyVerificationOverride(buildOperationExecutor, checksumService, signatureVerificationServiceFactory, documentationRegistry, timeProvider, () -> serviceRegistry.get(GradleProperties.class));
        registerBuildFinishedHooks(listenerManager, override);
        return override;
    }

    ResolveIvyFactory createResolveIvyFactory(StartParameterResolutionOverride startParameterResolutionOverride,
                                              ModuleRepositoryCacheProvider moduleRepositoryCacheProvider,
                                              DependencyVerificationOverride dependencyVerificationOverride,
                                              BuildCommencedTimeProvider buildCommencedTimeProvider,
                                              VersionComparator versionComparator,
                                              ImmutableModuleIdentifierFactory moduleIdentifierFactory,
                                              RepositoryDisabler repositoryBlacklister,
                                              VersionParser versionParser,
                                              ListenerManager listenerManager) {
        return new ResolveIvyFactory(
            moduleRepositoryCacheProvider,
            startParameterResolutionOverride,
            dependencyVerificationOverride,
            buildCommencedTimeProvider,
            versionComparator,
            moduleIdentifierFactory,
            repositoryBlacklister,
            versionParser,
            listenerManager.getBroadcaster(ChangingValueDependencyResolutionListener.class)
        );
    }

    ComponentSelectionDescriptorFactory createComponentSelectionDescriptorFactory() {
        return new CachingComponentSelectionDescriptorFactory();
    }

    ArtifactDependencyResolver createArtifactDependencyResolver(ResolveIvyFactory resolveIvyFactory,
                                                                DependencyDescriptorFactory dependencyDescriptorFactory,
                                                                VersionComparator versionComparator,
                                                                List<ResolverProviderFactory> resolverFactories,
                                                                ProjectDependencyResolver projectDependencyResolver,
                                                                ModuleExclusions moduleExclusions,
                                                                BuildOperationExecutor buildOperationExecutor,
                                                                ComponentSelectorConverter componentSelectorConverter,
                                                                ImmutableAttributesFactory attributesFactory,
                                                                VersionSelectorScheme versionSelectorScheme,
                                                                VersionParser versionParser,
                                                                ComponentMetadataSupplierRuleExecutor componentMetadataSupplierRuleExecutor,
                                                                InstantiatorFactory instantiatorFactory,
                                                                ComponentSelectionDescriptorFactory componentSelectionDescriptorFactory) {
        return new DefaultArtifactDependencyResolver(
            buildOperationExecutor,
            resolverFactories,
            projectDependencyResolver,
            resolveIvyFactory,
            dependencyDescriptorFactory,
            versionComparator,
            moduleExclusions,
            componentSelectorConverter,
            attributesFactory,
            versionSelectorScheme,
            versionParser,
            componentMetadataSupplierRuleExecutor,
            instantiatorFactory,
            componentSelectionDescriptorFactory);
    }

    ProjectPublicationRegistry createProjectPublicationRegistry() {
        return new DefaultProjectPublicationRegistry();
    }

    LocalComponentProvider createProjectComponentProvider(ProjectStateRegistry projectStateRegistry, ProjectRegistry<ProjectInternal> projectRegistry, LocalComponentMetadataBuilder metaDataBuilder, ImmutableModuleIdentifierFactory moduleIdentifierFactory, BuildState currentBuild) {
        return new DefaultProjectLocalComponentProvider(projectStateRegistry, projectRegistry, metaDataBuilder, moduleIdentifierFactory, currentBuild.getBuildIdentifier());
    }

    LocalComponentRegistry createLocalComponentRegistry(List<LocalComponentProvider> providers) {
        return new DefaultLocalComponentRegistry(providers);
    }

    ComponentSelectorConverter createModuleVersionSelectorFactory(ComponentIdentifierFactory componentIdentifierFactory, LocalComponentRegistry localComponentRegistry) {
        return new DefaultComponentSelectorConverter(componentIdentifierFactory, localComponentRegistry);
    }

    VersionParser createVersionParser() {
        return new VersionParser();
    }

    VersionSelectorScheme createVersionSelectorScheme(VersionComparator versionComparator, VersionParser versionParser, FeaturePreviews featurePreviews, ListenerManager listenerManager) {
        DefaultVersionSelectorScheme delegate = new DefaultVersionSelectorScheme(versionComparator, versionParser, featurePreviews);
        CachingVersionSelectorScheme selectorScheme = new CachingVersionSelectorScheme(delegate, featurePreviews);
        // This needs to be removed once the feature preview disappears
        listenerManager.addListener(new BuildAdapter() {
            @Override
            public void settingsEvaluated(Settings settings) {
                delegate.configure();
                selectorScheme.configure();
            }
        });

        return selectorScheme;
    }

    SimpleMapInterner createStringInterner() {
        return SimpleMapInterner.threadSafe();
    }

    ModuleComponentResolveMetadataSerializer createModuleComponentResolveMetadataSerializer(ImmutableAttributesFactory attributesFactory, MavenMutableModuleMetadataFactory mavenMetadataFactory, IvyMutableModuleMetadataFactory ivyMetadataFactory, ImmutableModuleIdentifierFactory moduleIdentifierFactory, NamedObjectInstantiator instantiator, ModuleSourcesSerializer moduleSourcesSerializer) {
        DesugaringAttributeContainerSerializer attributeContainerSerializer = new DesugaringAttributeContainerSerializer(attributesFactory, instantiator);
        return new ModuleComponentResolveMetadataSerializer(new ModuleMetadataSerializer(attributeContainerSerializer, mavenMetadataFactory, ivyMetadataFactory, moduleSourcesSerializer), attributeContainerSerializer, moduleIdentifierFactory);
    }

    SuppliedComponentMetadataSerializer createSuppliedComponentMetadataSerializer(ImmutableModuleIdentifierFactory moduleIdentifierFactory, AttributeContainerSerializer attributeContainerSerializer) {
        ModuleVersionIdentifierSerializer moduleVersionIdentifierSerializer = new ModuleVersionIdentifierSerializer(moduleIdentifierFactory);
        return new SuppliedComponentMetadataSerializer(moduleVersionIdentifierSerializer, attributeContainerSerializer);
    }

    ComponentMetadataRuleExecutor createComponentMetadataRuleExecutor(ValueSnapshotter valueSnapshotter,
                                                                      CacheRepository cacheRepository,
                                                                      InMemoryCacheDecoratorFactory cacheDecoratorFactory,
                                                                      BuildCommencedTimeProvider timeProvider,
                                                                      ModuleComponentResolveMetadataSerializer serializer) {
        return new ComponentMetadataRuleExecutor(cacheRepository, cacheDecoratorFactory, valueSnapshotter, timeProvider, serializer);
    }

    ComponentMetadataSupplierRuleExecutor createComponentMetadataSupplierRuleExecutor(ValueSnapshotter snapshotter,
                                                                                      CacheRepository cacheRepository,
                                                                                      InMemoryCacheDecoratorFactory cacheDecoratorFactory,
                                                                                      final BuildCommencedTimeProvider timeProvider,
                                                                                      SuppliedComponentMetadataSerializer suppliedComponentMetadataSerializer,
                                                                                      ListenerManager listenerManager) {
        if (cacheDecoratorFactory instanceof CleaningInMemoryCacheDecoratorFactory) {
            listenerManager.addListener(new InternalBuildFinishedListener() {
                @Override
                public void buildFinished(GradleInternal build) {
                    ((CleaningInMemoryCacheDecoratorFactory) cacheDecoratorFactory).clearCaches(ComponentMetadataRuleExecutor::isMetadataRuleExecutorCache);
                }
            });
        }
        return new ComponentMetadataSupplierRuleExecutor(cacheRepository, cacheDecoratorFactory, snapshotter, timeProvider, suppliedComponentMetadataSerializer);
    }

    SignatureVerificationServiceFactory createSignatureVerificationServiceFactory(CacheRepository cacheRepository,
                                                                                  InMemoryCacheDecoratorFactory decoratorFactory,
                                                                                  List<ResourceConnectorFactory> resourceConnectorFactories,
                                                                                  BuildOperationExecutor buildOperationExecutor,
                                                                                  BuildCommencedTimeProvider timeProvider,
                                                                                  FileHasher fileHasher,
                                                                                  CacheScopeMapping scopeCacheMapping,
                                                                                  ProjectCacheDir projectCacheDir,
                                                                                  StartParameter startParameter) {
        HttpConnectorFactory httpConnectorFactory = null;
        for (ResourceConnectorFactory factory : resourceConnectorFactories) {
            if (factory instanceof HttpConnectorFactory) {
                httpConnectorFactory = (HttpConnectorFactory) factory;
                break;
            }
        }
        if (httpConnectorFactory == null) {
            throw new IllegalStateException("Cannot find HttpConnectorFactory");
        }
        return new DefaultSignatureVerificationServiceFactory(httpConnectorFactory, cacheRepository, decoratorFactory, buildOperationExecutor, fileHasher, scopeCacheMapping, projectCacheDir, timeProvider, startParameter.isRefreshKeys());
    }

    private void registerBuildFinishedHooks(ListenerManager listenerManager, DependencyVerificationOverride dependencyVerificationOverride) {
        listenerManager.addListener(new InternalBuildFinishedListener() {
            @Override
            public void buildFinished(GradleInternal build) {
                dependencyVerificationOverride.buildFinished(build);
            }
        });
    }
}
