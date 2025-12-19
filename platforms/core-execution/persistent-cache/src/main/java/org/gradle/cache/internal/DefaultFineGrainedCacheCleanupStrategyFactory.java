/*
 * Copyright 2025 the original author or authors.
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
import org.gradle.cache.CacheCleanupStrategyFactory;
import org.gradle.cache.CleanupFrequency;
import org.gradle.cache.FineGrainedCacheCleanupStrategy;
import org.gradle.cache.FineGrainedCacheCleanupStrategyFactory;
import org.gradle.cache.FineGrainedLeastRecentlyUsedCacheCleanup;
import org.gradle.cache.FineGrainedPersistentCache;
import org.gradle.cache.internal.DefaultFineGrainedLeastRecentlyUsedCacheCleanup.FineGrainedCacheLeastRecentlyUsedMarkAndSweepDeleter;
import org.gradle.internal.file.Deleter;
import org.gradle.internal.file.FileAccessTimeJournal;

import java.util.function.Supplier;

public class DefaultFineGrainedCacheCleanupStrategyFactory implements FineGrainedCacheCleanupStrategyFactory {

    private final FileAccessTimeJournal fileAccessTimeJournal;
    private final Deleter deleter;
    private final CacheCleanupStrategyFactory cacheCleanupStrategyFactory;

    public DefaultFineGrainedCacheCleanupStrategyFactory(CacheCleanupStrategyFactory cacheCleanupStrategyFactory, FileAccessTimeJournal fileAccessTimeJournal, Deleter deleter) {
        this.cacheCleanupStrategyFactory = cacheCleanupStrategyFactory;
        this.fileAccessTimeJournal = fileAccessTimeJournal;
        this.deleter = deleter;
    }

    @Override
    public FineGrainedCacheCleanupStrategy leastRecentlyUsedStrategy(int cacheDepth, Supplier<Long> cacheEntryRetentionTimestamp, Supplier<CleanupFrequency> frequency) {
        FineGrainedLeastRecentlyUsedCacheCleanup cleanup = new DefaultFineGrainedLeastRecentlyUsedCacheCleanup(
            deleter,
            cacheDepth,
            fileAccessTimeJournal,
            cacheEntryRetentionTimestamp
        );
        CacheCleanupStrategy cacheCleanupStrategy = cacheCleanupStrategyFactory.create(cleanup, frequency);
        return new FineGrainedCacheCleanupStrategy() {
            @Override
            public CacheCleanupStrategy getCleanupStrategy(FineGrainedPersistentCache cache) {
                return cacheCleanupStrategy;
            }

            @Override
            public FineGrainedCacheMarkAndSweepDeleter getCacheDeleter(FineGrainedPersistentCache cache) {
                return new FineGrainedCacheLeastRecentlyUsedMarkAndSweepDeleter(cache, DefaultFineGrainedCacheCleanupStrategyFactory.this.deleter);
            }
        };
    }
}
