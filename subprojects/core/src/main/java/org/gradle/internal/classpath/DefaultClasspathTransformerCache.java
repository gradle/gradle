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

import org.gradle.cache.CacheBuilder;
import org.gradle.cache.CacheRepository;
import org.gradle.cache.FileLockManager;
import org.gradle.cache.PersistentCache;
import org.gradle.cache.internal.CacheVersionMapping;
import org.gradle.cache.internal.CompositeCleanupAction;
import org.gradle.cache.internal.LeastRecentlyUsedCacheCleanup;
import org.gradle.cache.internal.SingleDepthFilesFinder;
import org.gradle.cache.internal.UnusedVersionsCacheCleanup;
import org.gradle.cache.internal.UsedGradleVersions;
import org.gradle.internal.file.FileAccessTimeJournal;

import java.io.Closeable;
import java.io.File;
import java.util.Collections;
import java.util.List;

import static org.gradle.cache.internal.CacheVersionMapping.introducedIn;
import static org.gradle.cache.internal.LeastRecentlyUsedCacheCleanup.DEFAULT_MAX_AGE_IN_DAYS_FOR_RECREATABLE_CACHE_ENTRIES;
import static org.gradle.cache.internal.filelock.LockOptionsBuilder.mode;

public class DefaultClasspathTransformerCache implements ClasspathTransformerCache, Closeable {
    public static final CacheVersionMapping CACHE_VERSION_MAPPING = introducedIn("3.1-rc-1").incrementedIn("3.2-rc-1").incrementedIn("3.5-rc-1").build();
    public static final String CACHE_NAME = "jars";
    public static final String CACHE_KEY = CACHE_NAME + "-" + CACHE_VERSION_MAPPING.getLatestVersion();
    private static final int FILE_TREE_DEPTH_TO_TRACK_AND_CLEANUP = 1;

    private final PersistentCache cache;

    public DefaultClasspathTransformerCache(
        CacheRepository cacheRepository,
        FileAccessTimeJournal fileAccessTimeJournal,
        UsedGradleVersions usedGradleVersions
    ) {
        this.cache = cacheRepository
            .cache(CACHE_KEY)
            .withDisplayName(CACHE_NAME)
            .withCrossVersionCache(CacheBuilder.LockTarget.DefaultTarget)
            .withLockOptions(mode(FileLockManager.LockMode.None))
            .withCleanup(CompositeCleanupAction.builder()
                .add(UnusedVersionsCacheCleanup.create(CACHE_NAME, CACHE_VERSION_MAPPING, usedGradleVersions))
                .add(new LeastRecentlyUsedCacheCleanup(new SingleDepthFilesFinder(FILE_TREE_DEPTH_TO_TRACK_AND_CLEANUP), fileAccessTimeJournal, DEFAULT_MAX_AGE_IN_DAYS_FOR_RECREATABLE_CACHE_ENTRIES))
                .build())
            .open();
    }

    @Override
    public PersistentCache getCache() {
        return cache;
    }

    @Override
    public List<File> getFileStoreRoots() {
        return Collections.singletonList(cache.getBaseDir());
    }

    @Override
    public void close() {
        cache.close();
    }
}
