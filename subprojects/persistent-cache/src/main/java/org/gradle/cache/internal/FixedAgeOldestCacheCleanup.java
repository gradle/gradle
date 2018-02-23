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

import com.google.common.collect.Lists;
import org.gradle.cache.PersistentCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Deletes any cache entries older than a given age.
 */
public class FixedAgeOldestCacheCleanup extends AbstractCacheCleanup {
    private static final Logger LOGGER = LoggerFactory.getLogger(FixedAgeOldestCacheCleanup.class);

    private final long minimumTimestamp;

    public FixedAgeOldestCacheCleanup(long ageInDays) {
        this.minimumTimestamp = Math.max(0, System.currentTimeMillis() - TimeUnit.DAYS.toMillis(ageInDays));
    }

    @Override
    protected List<File> findFilesToDelete(final PersistentCache persistentCache, File[] filesEligibleForCleanup) {
        LOGGER.info("{} remove files older than {}.", persistentCache, new Date(minimumTimestamp));

        List<File> filesForDeletion = Lists.newArrayListWithCapacity(filesEligibleForCleanup.length);

        for (File file : filesEligibleForCleanup) {
            if (file.lastModified() < minimumTimestamp) {
                filesForDeletion.add(file);
            }
        }

        return filesForDeletion;
    }
}
