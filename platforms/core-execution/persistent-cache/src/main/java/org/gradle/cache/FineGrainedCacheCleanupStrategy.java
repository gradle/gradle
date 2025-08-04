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

import java.io.File;

/**
 * A strategy for cleaning up fine-grained persistent caches.
 * This interface provides methods to obtain a cleanup strategy and a deleter for cache entries.
 */
public interface FineGrainedCacheCleanupStrategy {

    CacheCleanupStrategy getCleanupStrategy(FineGrainedPersistentCache cache);

    FineGrainedCacheDeleter getCacheDeleter();

    /*
     * A deleter for fine-grained cache entries.
     */
    interface FineGrainedCacheDeleter {
        /**
         * Checks if the given cache entry is stale.
         * A stale entry is one that is no longer valid, for example, if it has not been accessed for a certain period of time.
         *
         * Such entry will be deleted by the deleter after some period.
         */
        boolean isStale(File entry);

        /**
         * Unstales the given cache entry.
         * This method is called when an entry is accessed, indicating that it is still valid and should not be deleted.
         *
         * This should be called only if the entry lock is held, to ensure that the entry is not concurrently modified.
         */
        void unstale(File entry);

        /**
         * Deletes the given cache entry.
         *
         * This should be called only if the entry lock is held, to ensure that the entry is not concurrently modified.
         */
        boolean delete(File entry);
    }
}
