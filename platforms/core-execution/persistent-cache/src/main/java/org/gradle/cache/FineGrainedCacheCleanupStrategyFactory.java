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

package org.gradle.cache;

import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

import java.util.function.Supplier;

/**
 * A factory for creating fine-grained cache cleanup strategies.
 */
@ServiceScope(Scope.UserHome.class)
public interface FineGrainedCacheCleanupStrategyFactory {

    /**
     * Creates a CacheCleanupStrategy that cleans up the cache entry based on the least recently used (LRU) algorithm.
     *
     * The entry is first marked as stale and only then deleted after a certain period of time.
     */
    FineGrainedCacheCleanupStrategy leastRecentlyUsedStrategy(int cacheDepth, Supplier<Long> cacheEntryRetentionTimestamp, Supplier<CleanupFrequency> frequency);

}
