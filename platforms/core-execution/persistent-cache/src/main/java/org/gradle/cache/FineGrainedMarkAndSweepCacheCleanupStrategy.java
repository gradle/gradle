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

import org.gradle.internal.file.FileAccessTimeJournal;

import java.io.File;
import java.time.Duration;

/**
 * A {@link FineGrainedCacheCleanupStrategy} that uses a {@link FineGrainedCacheEntrySoftDeleter} to determine which entries are soft deleted.
 *
 * This strategy first marks entries as soft deleted and after a {@link FineGrainedMarkAndSweepCacheCleanupStrategy#SOFT_DELETION_DURATION} period of time hard deletes them.
 * The process that reads/writes to cache, can use {@link FineGrainedCacheEntrySoftDeleter#isSoftDeleted(String)} to determine which entries are soft deleted.
 * Soft deleted entries should then not be read without the lock.
 * The process can remove the soft delete marker via {@link FineGrainedCacheEntrySoftDeleter#removeSoftDeleteMarker(String)} if it determines the entry should not be hard deleted.
 */
public interface FineGrainedMarkAndSweepCacheCleanupStrategy extends FineGrainedCacheCleanupStrategy {

    /**
     * A duration after which entries are hard deleted.
     * <p>
     * Rationale for the chosen value (6 hours):<br/>
     * - When an entry is soft-deleted, there might still be processes that picked it up moments earlier.
     *   Those processes need time to finish safely without the entry disappearing underneath them.<br/>
     * - Processes that picked an entry up moments earlier mark entry last access time, but
     *   updating last access times can be asynchronous. Implementations of
     *   {@link FileAccessTimeJournal#setLastAccessTime(File, long)} may delay
     *   persisting the written timestamp. The 6-hour window is large enough so that, even if the
     *   access time update is buffered or written later (e.g. on daemon stop or a periodic flush),
     *   the journal update should be picked up by subsequent cleanup passes and prevent premature
     *   hard deletion of actively used entries.
     * </p>
     * In short, 6 hours is a safety buffer: long enough to be robust against asynchronous journal
     * writes and in-flight work, yet short enough to eventually free disk space for truly unused
     * entries.
     */
    Duration SOFT_DELETION_DURATION = Duration.ofHours(6);

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
