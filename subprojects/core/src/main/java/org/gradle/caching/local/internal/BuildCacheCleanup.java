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

package org.gradle.caching.local.internal;

import com.google.common.collect.Lists;
import com.google.common.collect.Ordering;
import org.apache.commons.io.FileUtils;
import org.gradle.api.Action;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.cache.PersistentCache;

import java.io.File;
import java.io.FilenameFilter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

final class BuildCacheCleanup implements Action<PersistentCache> {
    private static final Logger LOGGER = Logging.getLogger(BuildCacheCleanup.class);
    private static final Comparator<File> NEWEST_FIRST = new Comparator<File>() {
        @Override
        public int compare(File o1, File o2) {
            // Sort with the oldest last
            return Ordering.natural().compare(o2.lastModified(), o1.lastModified());
        }
    };

    private final Pattern cacheEntryPattern = Pattern.compile("\\p{XDigit}{32}(.part)?");
    private final long targetSizeInMB;

    BuildCacheCleanup(long targetSizeInMB) {
        this.targetSizeInMB = targetSizeInMB;
    }

    @Override
    public void execute(PersistentCache persistentCache) {
        File[] filesEligibleForCleanup = findEligibleFilesForDeletion(persistentCache);
        cleanupEligibleFiles(persistentCache, filesEligibleForCleanup);
    }

    private File[] findEligibleFilesForDeletion(PersistentCache persistentCache) {
        final File cacheDir = persistentCache.getBaseDir();
        return cacheDir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return canBeDeleted(name);
            }
        });
    }

    private void cleanupEligibleFiles(PersistentCache persistentCache, File[] filesEligibleForCleanup) {
        final File cacheDir = persistentCache.getBaseDir();

        Arrays.sort(filesEligibleForCleanup, NEWEST_FIRST);

        // All sizes are in bytes
        long removedSize = 0;
        long totalSize = 0;
        long targetSize = targetSizeInMB * 1024 * 1024;
        final List<File> filesForDeletion = Lists.newArrayList();

        for (File file : filesEligibleForCleanup) {
            long size = file.length();
            totalSize += size;

            if (totalSize > targetSize) {
                removedSize += size;
                filesForDeletion.add(file);
            }
        }

        LOGGER.info("Build cache ({}) consuming {} MB (target: {} MB).", cacheDir, FileUtils.byteCountToDisplaySize(totalSize), targetSizeInMB);

        if (!filesForDeletion.isEmpty()) {
            // Need to remove some files
            persistentCache.useCache(new Runnable() {
                @Override
                public void run() {
                    deleteFile(filesForDeletion);
                }
            });
            LOGGER.info("Build cache ({}) removing {} cache entries ({} MB reclaimed).", cacheDir, filesForDeletion.size(), FileUtils.byteCountToDisplaySize(removedSize));
        }
    }

    private void deleteFile(List<File> files) {
        for (File file : files) {
            try {
                file.delete();
            } catch (Exception e) {
                LOGGER.debug("Could not clean up cache entry " + file, e);
            }
        }
    }

    private boolean canBeDeleted(String name) {
        return cacheEntryPattern.matcher(name).matches();
    }
}
