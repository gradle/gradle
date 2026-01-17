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
import org.gradle.cache.CacheCleanupStrategyFactory;
import org.gradle.cache.CleanupFrequency;
import org.gradle.cache.FineGrainedCacheCleanupStrategyFactory;
import org.gradle.cache.FineGrainedMarkAndSweepCacheCleanupStrategy;
import org.gradle.cache.FineGrainedPersistentCache;
import org.gradle.internal.file.FileAccessTimeJournal;
import org.jspecify.annotations.NullMarked;

import java.util.function.Supplier;

@NullMarked
public class DefaultFineGrainedCacheCleanupStrategyFactory implements FineGrainedCacheCleanupStrategyFactory {

    private final FileAccessTimeJournal fileAccessTimeJournal;
    private final CacheCleanupStrategyFactory cacheCleanupStrategyFactory;

    public DefaultFineGrainedCacheCleanupStrategyFactory(CacheCleanupStrategyFactory cacheCleanupStrategyFactory, FileAccessTimeJournal fileAccessTimeJournal) {
        this.cacheCleanupStrategyFactory = cacheCleanupStrategyFactory;
        this.fileAccessTimeJournal = fileAccessTimeJournal;
    }

    @Override
    public FineGrainedMarkAndSweepCacheCleanupStrategy markAndSweepCleanupStrategy(Supplier<Long> cacheEntryRetentionTimestamp, Supplier<CleanupFrequency> frequency) {
        FineGrainedMarkAndSweepLeastRecentlyUsedCacheCleanup cleanup = new FineGrainedMarkAndSweepLeastRecentlyUsedCacheCleanup(fileAccessTimeJournal, cacheEntryRetentionTimestamp);
        CacheCleanupStrategy cacheCleanupStrategy = cacheCleanupStrategyFactory.create(cleanup, frequency);
        return new FineGrainedMarkAndSweepCacheCleanupStrategy() {
            @Override
            public FineGrainedCacheEntrySoftDeleter getSoftDeleter(FineGrainedPersistentCache cache) {
                return cleanup.getSoftDeleter(cache);
            }

            @Override
            public CacheCleanupStrategy getCleanupStrategy() {
                return cacheCleanupStrategy;
            }
        };
    }
}
