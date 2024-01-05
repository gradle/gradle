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
import org.gradle.cache.FileLockManager;
import org.gradle.cache.PersistentCache;
import org.gradle.cache.scopes.ScopedCacheBuilderFactory;

import java.io.File;

/**
 * The default implementation of {@link DecompressionCoordinator} that can be used to store decompressed data extracted from archive files like zip and tars.
 *
 * Will manage access to the cache, so that access to the archive's contents are only permitted to one client at a time.
 */
public class DefaultDecompressionCoordinator implements DecompressionCoordinator {
    private static final String EXPANSION_CACHE_KEY = "expanded";
    private static final String EXPANSION_CACHE_NAME = "Compressed Files Expansion Cache";
    private final PersistentCache cache;
    private final ProducerGuard<File> guard = ProducerGuard.adaptive();

    public DefaultDecompressionCoordinator(ScopedCacheBuilderFactory cacheBuilderFactory) {
        this.cache = cacheBuilderFactory.createCacheBuilder(EXPANSION_CACHE_KEY)
                .withDisplayName(EXPANSION_CACHE_NAME)
                .withInitialLockMode(FileLockManager.LockMode.OnDemand)
                .open();
    }

    @VisibleForTesting
    public DefaultDecompressionCoordinator(PersistentCache cache) {
        this.cache = cache;
    }

    @Override
    public void exclusiveAccessTo(File expandedDir, Runnable action) {
        // withFileLock prevents other processes from extracting into the expandedDir at the same time
        // but multiple threads in this process could still try to extract into the same directory.
        cache.withFileLock(() -> {
            // guardByKey prevents multiple threads in this process from extracting into the same directory at the same time.
            guard.guardByKey(expandedDir, () -> {
                action.run();
                return null;
            });
        });
    }

    @Override
    public void close() {
        cache.close();
    }
}
