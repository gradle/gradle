/*
 * Copyright 2017 the original author or authors.
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
import org.gradle.StartParameter;
import org.gradle.api.internal.artifacts.ivyservice.ArtifactCacheLockingAccessCoordinator;
import org.gradle.api.internal.artifacts.ivyservice.ArtifactCacheMetadata;
import org.gradle.api.internal.artifacts.ivyservice.ArtifactCachesProvider;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ConnectionFailureRepositoryDisabler;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleDescriptorHashCodec;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleDescriptorHashModuleSource;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.StartParameterResolutionOverride;
import org.gradle.api.internal.artifacts.ivyservice.modulecache.AbstractModuleMetadataCache;
import org.gradle.api.internal.artifacts.ivyservice.modulecache.FileStoreAndIndexProvider;
import org.gradle.api.internal.artifacts.ivyservice.modulecache.InMemoryModuleMetadataCache;
import org.gradle.api.internal.artifacts.ivyservice.modulecache.ModuleRepositoryCacheProvider;
import org.gradle.api.internal.artifacts.ivyservice.modulecache.ModuleRepositoryCaches;
import org.gradle.api.internal.artifacts.ivyservice.modulecache.ModuleSourcesSerializer;
import org.gradle.api.internal.artifacts.ivyservice.modulecache.PersistentModuleMetadataCache;
import org.gradle.api.internal.artifacts.ivyservice.modulecache.ReadOnlyModuleMetadataCache;
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
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectArtifactResolver;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.AttributeContainerSerializer;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ThisBuildOnlyComponentDetailsSerializer;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ThisBuildOnlySelectedVariantSerializer;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.store.ResolutionResultsStoreFactory;
import org.gradle.api.internal.artifacts.repositories.metadata.DefaultMetadataFileSourceCodec;
import org.gradle.api.internal.artifacts.repositories.metadata.IvyMutableModuleMetadataFactory;
import org.gradle.api.internal.artifacts.repositories.metadata.MavenMutableModuleMetadataFactory;
import org.gradle.api.internal.artifacts.repositories.metadata.MetadataFileSource;
import org.gradle.api.internal.artifacts.transform.TransformStepNodeFactory;
import org.gradle.api.internal.attributes.AttributeDesugaring;
import org.gradle.api.internal.file.temp.TemporaryFileProvider;
import org.gradle.api.internal.filestore.ArtifactIdentifierFileStore;
import org.gradle.api.internal.filestore.DefaultArtifactIdentifierFileStore;
import org.gradle.api.internal.filestore.TwoStageArtifactIdentifierFileStore;
import org.gradle.initialization.layout.BuildLayout;
import org.gradle.internal.component.external.model.ModuleComponentGraphResolveStateFactory;
import org.gradle.internal.component.local.model.LocalComponentGraphResolveStateFactory;
import org.gradle.internal.component.model.ComponentIdGenerator;
import org.gradle.internal.component.model.PersistentModuleSource;
import org.gradle.internal.hash.ChecksumService;
import org.gradle.internal.resource.cached.ByUrlCachedExternalResourceIndex;
import org.gradle.internal.resource.cached.CachedExternalResourceIndex;
import org.gradle.internal.resource.cached.DefaultExternalResourceFileStore;
import org.gradle.internal.resource.cached.ExternalResourceFileStore;
import org.gradle.internal.resource.cached.TwoStageByUrlCachedExternalResourceIndex;
import org.gradle.internal.resource.cached.TwoStageExternalResourceFileStore;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.util.internal.BuildCommencedTimeProvider;
import org.gradle.util.internal.SimpleMapInterner;

import java.io.File;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The set of dependency management services that are created per build tree.
 */
class DependencyManagementBuildTreeScopeServices {
    void configure(ServiceRegistration registration) {
        registration.add(ProjectArtifactResolver.class);
        registration.add(DefaultExternalResourceFileStore.Factory.class);
        registration.add(DefaultArtifactIdentifierFileStore.Factory.class);
        registration.add(TransformStepNodeFactory.class);
        registration.add(AttributeDesugaring.class);
        registration.add(ComponentIdGenerator.class);
        registration.add(LocalComponentGraphResolveStateFactory.class);
        registration.add(ModuleComponentGraphResolveStateFactory.class);
        registration.add(ThisBuildOnlyComponentDetailsSerializer.class);
        registration.add(ThisBuildOnlySelectedVariantSerializer .class);
        registration.add(ConnectionFailureRepositoryDisabler.class);
    }

    SimpleMapInterner createStringInterner() {
        return SimpleMapInterner.threadSafe();
    }

    BuildCommencedTimeProvider createBuildTimeProvider(StartParameter startParameter) {
        return new BuildCommencedTimeProvider(startParameter);
    }

    ResolutionResultsStoreFactory createResolutionResultsStoreFactory(TemporaryFileProvider temporaryFileProvider) {
        return new ResolutionResultsStoreFactory(temporaryFileProvider);
    }

    private ByUrlCachedExternalResourceIndex prepareArtifactUrlCachedResolutionIndex(BuildCommencedTimeProvider timeProvider, ArtifactCacheLockingAccessCoordinator cacheAccessCoordinator, ExternalResourceFileStore externalResourceFileStore, ArtifactCacheMetadata artifactCacheMetadata) {
        return new ByUrlCachedExternalResourceIndex(
            "resource-at-url",
            timeProvider,
            cacheAccessCoordinator,
            externalResourceFileStore.getFileAccessTracker(),
            artifactCacheMetadata.getCacheDir().toPath()
        );
    }

    FileStoreAndIndexProvider createFileStoreAndIndexProvider(
        BuildCommencedTimeProvider timeProvider,
        ArtifactCachesProvider artifactCaches,
        DefaultExternalResourceFileStore.Factory defaultExternalResourceFileStoreFactory,
        DefaultArtifactIdentifierFileStore.Factory defaultArtifactIdentifierFileStoreFactory
    ) {
        ExternalResourceFileStore writableFileStore = defaultExternalResourceFileStoreFactory.create(artifactCaches.getWritableCacheMetadata());
        ExternalResourceFileStore externalResourceFileStore = artifactCaches.withReadOnlyCache((md, manager) ->
            (ExternalResourceFileStore) new TwoStageExternalResourceFileStore(defaultExternalResourceFileStoreFactory.create(md), writableFileStore)).orElse(writableFileStore);
        CachedExternalResourceIndex<String> writableByUrlCachedExternalResourceIndex = prepareArtifactUrlCachedResolutionIndex(timeProvider, artifactCaches.getWritableCacheAccessCoordinator(), externalResourceFileStore, artifactCaches.getWritableCacheMetadata());
        ArtifactIdentifierFileStore writableArtifactIdentifierFileStore = artifactCaches.withWritableCache((md, manager) -> defaultArtifactIdentifierFileStoreFactory.create(md));
        ArtifactIdentifierFileStore artifactIdentifierFileStore = artifactCaches.withReadOnlyCache((md, manager) -> (ArtifactIdentifierFileStore) new TwoStageArtifactIdentifierFileStore(
            defaultArtifactIdentifierFileStoreFactory.create(md),
            writableArtifactIdentifierFileStore
        )).orElse(writableArtifactIdentifierFileStore);
        return new FileStoreAndIndexProvider(
            artifactCaches.withReadOnlyCache((md, manager) -> (CachedExternalResourceIndex<String>) new TwoStageByUrlCachedExternalResourceIndex(md.getCacheDir().toPath(), prepareArtifactUrlCachedResolutionIndex(timeProvider, manager, externalResourceFileStore, md), writableByUrlCachedExternalResourceIndex)).orElse(writableByUrlCachedExternalResourceIndex),
            externalResourceFileStore, artifactIdentifierFileStore);
    }

    ModuleSourcesSerializer createModuleSourcesSerializer(ImmutableModuleIdentifierFactory moduleIdentifierFactory, FileStoreAndIndexProvider fileStoreAndIndexProvider) {
        Map<Integer, PersistentModuleSource.Codec<? extends PersistentModuleSource>> codecs = ImmutableMap.of(
            MetadataFileSource.CODEC_ID, new DefaultMetadataFileSourceCodec(moduleIdentifierFactory, fileStoreAndIndexProvider.getArtifactIdentifierFileStore()),
            ModuleDescriptorHashModuleSource.CODEC_ID, new ModuleDescriptorHashCodec()
        );
        return new ModuleSourcesSerializer(codecs);
    }

    StartParameterResolutionOverride createStartParameterResolutionOverride(StartParameter startParameter, BuildLayout buildLayout) {
        File rootDirectory = buildLayout.getRootDirectory();
        File gradleDir = new File(rootDirectory, "gradle");
        return new StartParameterResolutionOverride(startParameter, gradleDir);
    }

    ModuleRepositoryCacheProvider createModuleRepositoryCacheProvider(
        BuildCommencedTimeProvider timeProvider,
        ImmutableModuleIdentifierFactory moduleIdentifierFactory,
        ArtifactCachesProvider artifactCaches,
        AttributeContainerSerializer attributeContainerSerializer,
        MavenMutableModuleMetadataFactory mavenMetadataFactory,
        IvyMutableModuleMetadataFactory ivyMetadataFactory,
        SimpleMapInterner stringInterner,
        FileStoreAndIndexProvider fileStoreAndIndexProvider,
        ModuleSourcesSerializer moduleSourcesSerializer,
        ChecksumService checksumService
    ) {
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

    private static ModuleRepositoryCaches prepareModuleRepositoryCaches(ArtifactCacheMetadata artifactCacheMetadata, ArtifactCacheLockingAccessCoordinator cacheAccessCoordinator, BuildCommencedTimeProvider timeProvider, ImmutableModuleIdentifierFactory moduleIdentifierFactory, AttributeContainerSerializer attributeContainerSerializer, MavenMutableModuleMetadataFactory mavenMetadataFactory, IvyMutableModuleMetadataFactory ivyMetadataFactory, SimpleMapInterner stringInterner, ArtifactIdentifierFileStore artifactIdentifierFileStore, ModuleSourcesSerializer moduleSourcesSerializer, ChecksumService checksumService) {
        DefaultModuleVersionsCache moduleVersionsCache = new DefaultModuleVersionsCache(
            timeProvider,
            cacheAccessCoordinator,
            moduleIdentifierFactory);
        PersistentModuleMetadataCache moduleMetadataCache = new PersistentModuleMetadataCache(
            timeProvider,
            cacheAccessCoordinator,
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
            cacheAccessCoordinator
        );
        DefaultModuleArtifactCache moduleArtifactCache = new DefaultModuleArtifactCache(
            "module-artifact",
            timeProvider,
            cacheAccessCoordinator,
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

    private static ModuleRepositoryCaches prepareReadOnlyModuleRepositoryCaches(ArtifactCacheMetadata artifactCacheMetadata, ArtifactCacheLockingAccessCoordinator cacheAccessCoordinator, BuildCommencedTimeProvider timeProvider, ImmutableModuleIdentifierFactory moduleIdentifierFactory, AttributeContainerSerializer attributeContainerSerializer, MavenMutableModuleMetadataFactory mavenMetadataFactory, IvyMutableModuleMetadataFactory ivyMetadataFactory, SimpleMapInterner stringInterner, ArtifactIdentifierFileStore artifactIdentifierFileStore, ModuleSourcesSerializer moduleSourcesSerializer, ChecksumService checksumService) {
        ReadOnlyModuleVersionsCache moduleVersionsCache = new ReadOnlyModuleVersionsCache(
            timeProvider,
            cacheAccessCoordinator,
            moduleIdentifierFactory);
        ReadOnlyModuleMetadataCache moduleMetadataCache = new ReadOnlyModuleMetadataCache(
            timeProvider,
            cacheAccessCoordinator,
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
            cacheAccessCoordinator
        );
        ReadOnlyModuleArtifactCache moduleArtifactCache = new ReadOnlyModuleArtifactCache(
            "module-artifact",
            timeProvider,
            cacheAccessCoordinator,
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
}
