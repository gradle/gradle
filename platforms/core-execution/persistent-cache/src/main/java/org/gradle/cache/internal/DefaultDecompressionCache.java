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
 * are only permitted to one client at a time.  The cache will be a Gradle cross version cache.
 */
public class DefaultDecompressionCache implements DecompressionCache {
    private static final String EXPANSION_CACHE_NAME = "Compressed Files Expansion Cache";

    private final PersistentCache cache;

    public DefaultDecompressionCache(ScopedCacheBuilderFactory cacheBuilderFactory) {
        this.cache = cacheBuilderFactory.createCrossVersionCacheBuilder(EXPANSION_CACHE_KEY)
                .withDisplayName(EXPANSION_CACHE_NAME)
                .withCrossVersionCache(CacheBuilder.LockTarget.DefaultTarget)
                .withLockOptions(mode(FileLockManager.LockMode.OnDemand)) //TODO: the documentation above says this is a cross-version cache, not it's not! should it be?
                .open();
    }

    @VisibleForTesting
    public DefaultDecompressionCache(PersistentCache cache) {
        this.cache = cache;
    }

    @Override
    public void useCache(Runnable action) {
        cache.useCache(action);
    }

    @Override
    public File getBaseDir() {
        return cache.getBaseDir();
    }

    @Override
    public void close() {
        cache.close();
    }
}
