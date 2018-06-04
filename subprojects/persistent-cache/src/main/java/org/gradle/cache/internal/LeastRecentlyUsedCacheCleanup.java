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

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import org.gradle.cache.CleanableStore;
import org.gradle.internal.Factory;
import org.gradle.internal.resource.local.FileAccessTimeReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Deletes any cache entries not accessed within the specified number of days.
 */
public class LeastRecentlyUsedCacheCleanup extends AbstractCacheCleanup {
    private static final Logger LOGGER = LoggerFactory.getLogger(LeastRecentlyUsedCacheCleanup.class);

    public static final long DEFAULT_MAX_AGE_IN_DAYS_FOR_RECREATABLE_CACHE_ENTRIES = 7;
    public static final long DEFAULT_MAX_AGE_IN_DAYS_FOR_EXTERNAL_CACHE_ENTRIES = 30;

    private final Factory<? extends FileAccessTimeReader> accessTimeReaderFactory;
    private final long minimumTimestamp;

    public LeastRecentlyUsedCacheCleanup(FilesFinder eligibleFilesFinder, Factory<? extends FileAccessTimeReader> accessTimeReaderFactory, long numberOfDays) {
        super(eligibleFilesFinder);
        this.accessTimeReaderFactory = accessTimeReaderFactory;
        this.minimumTimestamp = Math.max(0, System.currentTimeMillis() - TimeUnit.DAYS.toMillis(numberOfDays));
    }

    @Override
    protected List<File> findFilesToDelete(final CleanableStore cleanableStore, Collection<File> filesEligibleForCleanup) {
        LOGGER.info("{} remove files not accessed on or after {}.", cleanableStore.getDisplayName(), new Date(minimumTimestamp));

        FileAccessTimeReader accessTimeReader = Preconditions.checkNotNull(accessTimeReaderFactory.create());
        List<File> filesForDeletion = Lists.newArrayListWithCapacity(filesEligibleForCleanup.size());

        for (File file : filesEligibleForCleanup) {
            if (accessTimeReader.getLastAccessTime(file) < minimumTimestamp) {
                filesForDeletion.add(file);
            }
        }

        return filesForDeletion;
    }
}
