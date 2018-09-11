/*
 * Copyright 2016 the original author or authors.
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

import org.gradle.api.internal.artifacts.ivyservice.ArtifactCacheLockingManager;
import org.gradle.api.internal.artifacts.ivyservice.ArtifactCacheMetadata;
import org.gradle.api.internal.artifacts.ivyservice.DefaultArtifactCacheLockingManager;
import org.gradle.api.internal.artifacts.ivyservice.DefaultArtifactCacheMetadata;
import org.gradle.api.internal.artifacts.transform.DefaultTransformedFileCache;
import org.gradle.api.internal.artifacts.transform.TransformedFileCache;
import org.gradle.api.internal.changedetection.state.InMemoryCacheDecoratorFactory;
import org.gradle.cache.CacheRepository;
import org.gradle.cache.internal.CacheScopeMapping;
import org.gradle.cache.internal.UsedGradleVersions;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.resource.local.FileAccessTimeJournal;
import org.gradle.internal.snapshot.FileSystemSnapshotter;

public class DependencyManagementGradleUserHomeScopeServices {
    DefaultArtifactCacheMetadata createArtifactCacheMetaData(CacheScopeMapping cacheScopeMapping) {
        return new DefaultArtifactCacheMetadata(cacheScopeMapping);
    }

    ArtifactCacheLockingManager createArtifactCacheLockingManager(CacheRepository cacheRepository, ArtifactCacheMetadata artifactCacheMetadata, FileAccessTimeJournal fileAccessTimeJournal,
                                                                  UsedGradleVersions usedGradleVersions) {
        return new DefaultArtifactCacheLockingManager(cacheRepository, artifactCacheMetadata, fileAccessTimeJournal, usedGradleVersions);
    }

    TransformedFileCache createTransformedFileCache(ArtifactCacheMetadata artifactCacheMetadata, CacheRepository cacheRepository, InMemoryCacheDecoratorFactory cacheDecoratorFactory,
                                                    FileSystemSnapshotter fileSystemSnapshotter, ListenerManager listenerManager, FileAccessTimeJournal fileAccessTimeJournal) {
        DefaultTransformedFileCache transformedFileCache = new DefaultTransformedFileCache(artifactCacheMetadata, cacheRepository, cacheDecoratorFactory, fileSystemSnapshotter, fileAccessTimeJournal);
        listenerManager.addListener(transformedFileCache);
        return transformedFileCache;
    }

}
