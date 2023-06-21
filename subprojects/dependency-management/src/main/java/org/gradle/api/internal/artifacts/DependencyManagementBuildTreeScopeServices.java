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

import org.gradle.StartParameter;
import org.gradle.api.internal.artifacts.ivyservice.ArtifactCacheLockingAccessCoordinator;
import org.gradle.api.internal.artifacts.ivyservice.ArtifactCacheMetadata;
import org.gradle.api.internal.artifacts.ivyservice.ArtifactCachesProvider;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.StartParameterResolutionOverride;
import org.gradle.api.internal.artifacts.ivyservice.modulecache.FileStoreAndIndexProvider;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectArtifactResolver;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.store.ResolutionResultsStoreFactory;
import org.gradle.api.internal.artifacts.transform.TransformationNodeFactory;
import org.gradle.api.internal.file.temp.TemporaryFileProvider;
import org.gradle.api.internal.filestore.ArtifactIdentifierFileStore;
import org.gradle.api.internal.filestore.DefaultArtifactIdentifierFileStore;
import org.gradle.api.internal.filestore.TwoStageArtifactIdentifierFileStore;
import org.gradle.initialization.layout.BuildLayout;
import org.gradle.internal.resource.cached.ByUrlCachedExternalResourceIndex;
import org.gradle.internal.resource.cached.CachedExternalResourceIndex;
import org.gradle.internal.resource.cached.DefaultExternalResourceFileStore;
import org.gradle.internal.resource.cached.ExternalResourceFileStore;
import org.gradle.internal.resource.cached.TwoStageByUrlCachedExternalResourceIndex;
import org.gradle.internal.resource.cached.TwoStageExternalResourceFileStore;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.util.internal.BuildCommencedTimeProvider;

import java.io.File;

/**
 * The set of dependency management services that are created per build tree.
 */
class DependencyManagementBuildTreeScopeServices {
    void configure(ServiceRegistration registration) {
        registration.add(ProjectArtifactResolver.class);
        registration.add(DefaultExternalResourceFileStore.Factory.class);
        registration.add(DefaultArtifactIdentifierFileStore.Factory.class);
        registration.add(TransformationNodeFactory.class);
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

    StartParameterResolutionOverride createStartParameterResolutionOverride(StartParameter startParameter, BuildLayout buildLayout) {
        File rootDirectory = buildLayout.getRootDirectory();
        File gradleDir = new File(rootDirectory, "gradle");
        return new StartParameterResolutionOverride(startParameter, gradleDir);
    }
}
