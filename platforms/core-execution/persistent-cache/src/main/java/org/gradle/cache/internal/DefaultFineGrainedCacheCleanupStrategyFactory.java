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
import org.gradle.cache.CleanupAction;
import org.gradle.cache.CleanupFrequency;
import org.gradle.cache.FineGrainedCacheCleanupStrategy;
import org.gradle.cache.FineGrainedCacheCleanupStrategyFactory;
import org.gradle.cache.FineGrainedPersistentCache;
import org.gradle.cache.internal.DefaultFineGrainedLeastRecentlyUsedCacheCleanup.FineGrainedLeastRecentlyUsedCacheDeleter;
import org.gradle.internal.file.Deleter;
import org.gradle.internal.file.FileAccessTimeJournal;

import java.util.function.Supplier;

public class DefaultFineGrainedCacheCleanupStrategyFactory implements FineGrainedCacheCleanupStrategyFactory {

    private final FileAccessTimeJournal fileAccessTimeJournal;
    private final FineGrainedLeastRecentlyUsedCacheDeleter deleter;
    private final CacheCleanupStrategyFactory cacheCleanupStrategyFactory;

    public DefaultFineGrainedCacheCleanupStrategyFactory(CacheCleanupStrategyFactory cacheCleanupStrategyFactory, FileAccessTimeJournal fileAccessTimeJournal, Deleter deleter) {
        this.cacheCleanupStrategyFactory = cacheCleanupStrategyFactory;
        this.fileAccessTimeJournal = fileAccessTimeJournal;
        // Use single instance of deleter for all cleanup strategies, so it can share the same cache
        this.deleter = new FineGrainedLeastRecentlyUsedCacheDeleter(deleter);
    }

    @Override
    public FineGrainedCacheCleanupStrategy leastRecentlyUsedStrategy(int cacheDepth, Supplier<Long> cacheEntryRetentionTimestamp, Supplier<CleanupFrequency> frequency) {
        return new FineGrainedCacheCleanupStrategy() {
            @Override
            public CacheCleanupStrategy getCleanupStrategy(FineGrainedPersistentCache cache) {
                CleanupAction action = new DefaultFineGrainedLeastRecentlyUsedCacheCleanup(
                    cache,
                    deleter,
                    cacheDepth,
                    fileAccessTimeJournal,
                    cacheEntryRetentionTimestamp
                );
                return cacheCleanupStrategyFactory.create(action, frequency);
            }

            @Override
            public FineGrainedCacheDeleter getCacheDeleter() {
                return deleter;
            }
        };
    }
}
