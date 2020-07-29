/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.internal.classpath;

import com.google.common.annotations.VisibleForTesting;
import org.gradle.cache.CacheBuilder;
import org.gradle.cache.CacheRepository;
import org.gradle.cache.FileLockManager;
import org.gradle.cache.PersistentCache;
import org.gradle.cache.internal.CacheScopeMapping;
import org.gradle.cache.internal.CacheVersionMapping;
import org.gradle.cache.internal.CompositeCleanupAction;
import org.gradle.cache.internal.LeastRecentlyUsedCacheCleanup;
import org.gradle.cache.internal.SingleDepthFilesFinder;
import org.gradle.cache.internal.UnusedVersionsCacheCleanup;
import org.gradle.cache.internal.UsedGradleVersions;
import org.gradle.cache.internal.VersionStrategy;
import org.gradle.internal.file.FileAccessTimeJournal;
import org.gradle.internal.file.FileAccessTracker;
import org.gradle.internal.file.impl.SingleDepthFileAccessTracker;

import java.io.File;

import static org.gradle.cache.internal.CacheVersionMapping.introducedIn;
import static org.gradle.cache.internal.LeastRecentlyUsedCacheCleanup.DEFAULT_MAX_AGE_IN_DAYS_FOR_RECREATABLE_CACHE_ENTRIES;
import static org.gradle.cache.internal.filelock.LockOptionsBuilder.mode;

public class DefaultClasspathTransformerCacheFactory implements ClasspathTransformerCacheFactory {
    private static final CacheVersionMapping CACHE_VERSION_MAPPING = introducedIn("3.1-rc-1")
        .incrementedIn("3.2-rc-1")
        .incrementedIn("3.5-rc-1")
        .changedTo(8, "6.5-rc-1")
        .build();
    @VisibleForTesting
    static final String CACHE_NAME = "jars";
    @VisibleForTesting
    static final String CACHE_KEY = CACHE_NAME + "-" + CACHE_VERSION_MAPPING.getLatestVersion();
    private static final int FILE_TREE_DEPTH_TO_TRACK_AND_CLEANUP = 1;

    private final File cacheDir;
    private final UsedGradleVersions usedGradleVersions;

    public DefaultClasspathTransformerCacheFactory(CacheScopeMapping cacheScopeMapping, UsedGradleVersions usedGradleVersions) {
        this.cacheDir = cacheScopeMapping.getBaseDirectory(null, CACHE_KEY, VersionStrategy.SharedCache);
        this.usedGradleVersions = usedGradleVersions;
    }

    @Override
    public PersistentCache createCache(CacheRepository cacheRepository, FileAccessTimeJournal fileAccessTimeJournal) {
        return cacheRepository
            .cache(cacheDir)
            .withDisplayName(CACHE_NAME)
            .withCrossVersionCache(CacheBuilder.LockTarget.DefaultTarget)
            .withLockOptions(mode(FileLockManager.LockMode.OnDemand))
            .withCleanup(CompositeCleanupAction.builder()
                .add(UnusedVersionsCacheCleanup.create(CACHE_NAME, CACHE_VERSION_MAPPING, usedGradleVersions))
                .add(new LeastRecentlyUsedCacheCleanup(new SingleDepthFilesFinder(FILE_TREE_DEPTH_TO_TRACK_AND_CLEANUP), fileAccessTimeJournal, DEFAULT_MAX_AGE_IN_DAYS_FOR_RECREATABLE_CACHE_ENTRIES))
                .build())
            .open();
    }

    @Override
    public FileAccessTracker createFileAccessTracker(FileAccessTimeJournal fileAccessTimeJournal) {
        return new SingleDepthFileAccessTracker(fileAccessTimeJournal, cacheDir, FILE_TREE_DEPTH_TO_TRACK_AND_CLEANUP);
    }
}
