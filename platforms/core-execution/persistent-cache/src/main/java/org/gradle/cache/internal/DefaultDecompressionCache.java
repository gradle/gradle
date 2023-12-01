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

import org.gradle.cache.FileLockManager;
import org.gradle.cache.IndexedCache;
import org.gradle.cache.IndexedCacheParameters;
import org.gradle.cache.PersistentCache;
import org.gradle.cache.scopes.ScopedCacheBuilderFactory;

import java.io.File;

/**
 * The default implementation of {@link DecompressionCache} that can be used to store decompressed data extracted from archive files like zip and tars.
 *
 * Will manage access to the cache, so that access to the archive's contents
 * are only permitted to one client at a time.
 */
public class DefaultDecompressionCache implements DecompressionCache {
    private static final String EXPANSION_CACHE_KEY = "expanded";
    private static final String EXPANSION_CACHE_NAME = "Compressed Files Expansion Cache";

    private final PersistentCache cache;
    private final IndexedCache<File, File> index;

    public DefaultDecompressionCache(ScopedCacheBuilderFactory cacheBuilderFactory, InMemoryCacheDecoratorFactory inMemoryCacheDecoratorFactory) {
        this.cache = cacheBuilderFactory.createCacheBuilder(EXPANSION_CACHE_KEY)
                .withDisplayName(EXPANSION_CACHE_NAME)
                .withInitialLockMode(FileLockManager.LockMode.OnDemand)
                .open();
        this.index = cache.createIndexedCache(
            IndexedCacheParameters.of("expanded-files", File.class, File.class)
                .withCacheDecorator(inMemoryCacheDecoratorFactory.decorator(100, true))
        );
    }

    @Override
    public void useCache(File expandedDir, Runnable action) {
        index.get(expandedDir, () -> {
                action.run();
                return expandedDir;
        });
    }

    @Override
    public void close() {
        cache.close();
    }
}
