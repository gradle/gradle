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

/**
 * A {@link FineGrainedCacheCleanupStrategy} that uses a {@link FineGrainedCacheEntrySoftDeleter} to determine which entries are soft deleted.
 */
public interface FineGrainedMarkAndSweepCacheCleanupStrategy extends FineGrainedCacheCleanupStrategy {

    /**
     * Returns a {@link FineGrainedCacheEntrySoftDeleter} that can be used to determine which entries are soft deleted.
     */
    FineGrainedCacheEntrySoftDeleter getSoftDeleter(FineGrainedPersistentCache cache);

    interface FineGrainedCacheEntrySoftDeleter {
        /**
         * Returns true if the entry is soft deleted.
         */
        boolean isSoftDeleted(String key);

        /**
         * Removes soft delete marker. Entry is not soft deleted anymore after that.
         * It's advised that this is called when cache lock is held at the end of the operation that acquired the entry lock.
         */
        void removeSoftDeleteMarker(String key);
    }
}
