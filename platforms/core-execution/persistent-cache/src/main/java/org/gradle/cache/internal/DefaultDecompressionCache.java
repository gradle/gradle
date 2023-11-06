/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.cache.internal;

import com.google.common.annotations.VisibleForTesting;
import org.gradle.api.Action;
import org.gradle.cache.CacheBuilder;
import org.gradle.cache.FileLockManager;
import org.gradle.cache.PersistentCache;
import org.gradle.cache.scopes.ScopedCacheBuilderFactory;

import java.io.File;
import static org.gradle.cache.internal.filelock.LockOptionsBuilder.mode;

/**
 * The default implementation of {@link DecompressionCache} that can be used to store decompressed data extracted from archive files like zip and tars.
 *
 * Will manage access to the cache, so that access to the archive's contents
 * are only permitted to one client at a time.  The cache will be a Gradle cross version cache.  This cache is meant to be
 * per-project, so that in a multi-project build if multiple projects all want to work with zip files, they will not block
 * each other.
 *
 * The cache is created lazily upon any action that requires it to be open; and is then closed immediately (via {@link AutoCloseable}
 * when that action is complete.  This is necessary because this cache lives in the project build directory by default, and as such
 * should be deletable via a clean task at any time.
 *
 * The downside to this is that if multiple Gradle processes run in the same directory, and one cleans at the same another is using the cache,
 * inconsistent behavior may result.  This is a known limitation of the current implementation, and is acceptable as running multiple Gradle
 * processes in the same directory, where one is deleting files used by the other one, is not a supported use case and would have
 * other problems.  Cleaning this cache in one project of a multi-project build while accessing zip files in a different project
 * within that same build should be okay.
 */
public class DefaultDecompressionCache implements DecompressionCache {
    private static final String EXPANSION_CACHE_NAME = "Compressed Files Expansion Cache";

    private final CacheBuilder cacheBuilder;

    public DefaultDecompressionCache(ScopedCacheBuilderFactory cacheBuilderFactory) {
        this.cacheBuilder = cacheBuilderFactory.createCrossVersionCacheBuilder(EXPANSION_CACHE_KEY)
                .withDisplayName(EXPANSION_CACHE_NAME)
                .withCrossVersionCache(CacheBuilder.LockTarget.DefaultTarget)
                .withLockOptions(mode(FileLockManager.LockMode.OnDemand));
    }

    @VisibleForTesting
    public DefaultDecompressionCache(CacheBuilder cacheBuilder) {
        this.cacheBuilder = cacheBuilder;
    }

    @Override
    public void useCache(Runnable action) {
        performActionAndThenCloseCache(cache -> cache.useCache(action));
    }

    @Override
    public File getBaseDir() {
        File[] results = new File[1];
        performActionAndThenCloseCache(cache -> results[0] = cache.getBaseDir());
        return results[0];
    }

    private void performActionAndThenCloseCache(Action<PersistentCache> action) {
        try (PersistentCache cache = cacheBuilder.open()) {
            action.execute(cache);
        }
    }
}
