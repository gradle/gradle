/*
 * Copyright 2026 the original author or authors.
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

import org.gradle.cache.CacheCleanupStrategy;
import org.gradle.cache.CacheOpenException;
import org.gradle.cache.FineGrainedCacheBuilder;
import org.gradle.cache.FineGrainedCacheCleanupStrategy;
import org.gradle.cache.FineGrainedPersistentCache;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.File;

@NullMarked
public class DefaultFineGrainedCacheBuilder implements FineGrainedCacheBuilder {

    private final CacheFactory factory;
    private final File baseDir;
    @Nullable
    private FineGrainedCacheCleanupStrategy cleanupStrategy;
    private String displayName = "<unnamed>";

    public DefaultFineGrainedCacheBuilder(CacheFactory factory, File baseDir) {
        this.factory = factory;
        this.baseDir = baseDir;
    }

    @Override
    public FineGrainedCacheBuilder withDisplayName(String displayName) {
        this.displayName = displayName;
        return this;
    }

    @Override
    public FineGrainedCacheBuilder withCleanupStrategy(FineGrainedCacheCleanupStrategy cacheCleanupStrategy) {
        this.cleanupStrategy = cacheCleanupStrategy;
        return this;
    }

    @Override
    public FineGrainedPersistentCache open() throws CacheOpenException {
        FineGrainedCacheCleanupStrategy cleanupStrategy = this.cleanupStrategy == null
            ? () -> CacheCleanupStrategy.NO_CLEANUP
            : this.cleanupStrategy;
        return factory.openFineGrained(baseDir, displayName, cleanupStrategy);
    }
}
