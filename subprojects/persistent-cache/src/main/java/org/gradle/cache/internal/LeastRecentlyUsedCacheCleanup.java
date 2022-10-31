/*
 * Copyright 2017 the original author or authors.
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

import org.gradle.cache.CleanableStore;
import org.gradle.cache.CleanupProgressMonitor;
import org.gradle.internal.file.FileAccessTimeJournal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Deletes any cache entries not accessed within the specified number of days.
 */
public class LeastRecentlyUsedCacheCleanup extends AbstractCacheCleanup {
    private static final Logger LOGGER = LoggerFactory.getLogger(LeastRecentlyUsedCacheCleanup.class);

    private final FileAccessTimeJournal journal;
    private final Supplier<Integer> numberOfDays;

    public LeastRecentlyUsedCacheCleanup(FilesFinder eligibleFilesFinder, FileAccessTimeJournal journal, Supplier<Integer> numberOfDays) {
        super(eligibleFilesFinder);
        this.journal = journal;
        this.numberOfDays = numberOfDays;
    }

    private long getMinimumTimestamp() {
        return Math.max(0, System.currentTimeMillis() - TimeUnit.DAYS.toMillis(numberOfDays.get()));
    }

    @Override
    public void clean(CleanableStore cleanableStore, CleanupProgressMonitor progressMonitor) {
        LOGGER.info("{} removing files not accessed on or after {}.", cleanableStore.getDisplayName(), new Date(getMinimumTimestamp()));
        super.clean(cleanableStore, progressMonitor);
    }

    @Override
    protected boolean shouldDelete(File file) {
        return journal.getLastAccessTime(file) < getMinimumTimestamp();
    }

    @Override
    protected void handleDeletion(File file) {
        journal.deleteLastAccessTime(file);
    }
}
