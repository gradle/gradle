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

import com.google.common.base.Function;
import com.google.common.collect.Lists;
import com.google.common.collect.Ordering;
import org.apache.commons.io.FileUtils;
import org.gradle.cache.PersistentCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class FixedSizeOldestCacheCleanup extends AbstractCacheCleanup {
    private static final Logger LOGGER = LoggerFactory.getLogger(FixedSizeOldestCacheCleanup.class);
    private static final Comparator<File> NEWEST_FIRST = Ordering.natural().onResultOf(new Function<File, Comparable<Long>>() {
        @Override
        public Comparable<Long> apply(File input) {
            return input.lastModified();
        }
    }).reverse();

    private final long targetSizeInMB;

    public FixedSizeOldestCacheCleanup(long targetSizeInMB) {
        this.targetSizeInMB = targetSizeInMB;
    }

    protected List<File> findFilesToDelete(final PersistentCache persistentCache, File[] filesEligibleForCleanup) {
        Arrays.sort(filesEligibleForCleanup, NEWEST_FIRST);

        // All sizes are in bytes
        long totalSize = 0;
        long targetSize = targetSizeInMB * 1024 * 1024;
        final List<File> filesForDeletion = Lists.newArrayList();

        for (File file : filesEligibleForCleanup) {
            long size = file.length();
            totalSize += size;

            if (totalSize > targetSize) {
                filesForDeletion.add(file);
            }
        }

        LOGGER.info("{} consuming {} (target: {} MB).", persistentCache, FileUtils.byteCountToDisplaySize(totalSize), targetSizeInMB);

        return filesForDeletion;
    }
}
