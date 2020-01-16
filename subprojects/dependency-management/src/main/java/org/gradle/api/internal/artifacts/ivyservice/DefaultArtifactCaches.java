/*
 * Copyright 2020 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice;

import com.google.common.collect.ImmutableList;
import org.gradle.cache.CacheRepository;
import org.gradle.cache.internal.CacheScopeMapping;
import org.gradle.cache.internal.UsedGradleVersions;
import org.gradle.internal.file.FileAccessTimeJournal;
import org.gradle.internal.vfs.AdditiveCache;

import java.io.File;
import java.util.List;
import java.util.Optional;

public class DefaultArtifactCaches implements ArtifactCachesProvider, AdditiveCache {
    private final DefaultArtifactCacheMetadata writableCacheMetadata;
    private final DefaultArtifactCacheMetadata readOnlyCacheMetadata;
    private final WritableArtifactCacheLockingManager writableArtifactCacheLockingManager;
    private final ReadOnlyArtifactCacheLockingManager readOnlyArtifactCacheLockingManager;

    public DefaultArtifactCaches(CacheScopeMapping cacheScopeMapping, CacheRepository cacheRepository, FileAccessTimeJournal fileAccessTimeJournal, UsedGradleVersions usedGradleVersions) {
        writableCacheMetadata = new DefaultArtifactCacheMetadata(cacheScopeMapping);
        writableArtifactCacheLockingManager = new WritableArtifactCacheLockingManager(cacheRepository, writableCacheMetadata, fileAccessTimeJournal, usedGradleVersions);
        String roCache = System.getProperty(READONLY_CACHE_PROPERTY);
        if (roCache != null) {
            readOnlyCacheMetadata = new DefaultArtifactCacheMetadata(cacheScopeMapping, new File(roCache));
            readOnlyArtifactCacheLockingManager = new ReadOnlyArtifactCacheLockingManager(cacheRepository, readOnlyCacheMetadata);
        } else {
            readOnlyCacheMetadata = null;
            readOnlyArtifactCacheLockingManager = null;
        }
    }

    @Override
    public ArtifactCacheMetadata getWritableCacheMetadata() {
        return writableCacheMetadata;
    }

    @Override
    public Optional<ArtifactCacheMetadata> getReadOnlyCacheMetadata() {
        return Optional.ofNullable(readOnlyCacheMetadata);
    }

    @Override
    public ArtifactCacheLockingManager getWritableCacheLockingManager() {
        return writableArtifactCacheLockingManager;
    }

    @Override
    public Optional<ArtifactCacheLockingManager> getReadOnlyCacheLockingManager() {
        return Optional.ofNullable(readOnlyArtifactCacheLockingManager);
    }

    @Override
    public List<File> getAdditiveCacheRoots() {
        ImmutableList.Builder<File> builder = ImmutableList.builderWithExpectedSize(4);
        builder.add(writableCacheMetadata.getFileStoreDirectory());
        builder.add(writableCacheMetadata.getTransformsStoreDirectory());
        if (readOnlyCacheMetadata != null) {
            builder.add(readOnlyCacheMetadata.getFileStoreDirectory());
            builder.add(readOnlyCacheMetadata.getTransformsStoreDirectory());
        }
        return builder.build();
    }

    @Override
    public void close() {
        writableArtifactCacheLockingManager.close();
        if (readOnlyArtifactCacheLockingManager != null) {
            readOnlyArtifactCacheLockingManager.close();
        }
    }
}
