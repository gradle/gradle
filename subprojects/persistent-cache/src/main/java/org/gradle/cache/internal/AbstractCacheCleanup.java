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

import com.google.common.annotations.VisibleForTesting;
import org.apache.commons.io.FileUtils;
import org.gradle.cache.CleanupAction;
import org.gradle.cache.PersistentCache;
import org.gradle.util.GFileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileFilter;
import java.util.List;

abstract class AbstractCacheCleanup implements CleanupAction {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractCacheCleanup.class);

    @Override
    public void clean(final PersistentCache persistentCache) {
        final File[] filesEligibleForCleanup = findEligibleFiles(persistentCache);

        if (filesEligibleForCleanup.length > 0) {
            final List<File> filesForDeletion = findFilesToDelete(persistentCache, filesEligibleForCleanup);

            if (!filesForDeletion.isEmpty()) {
                cleanupFiles(persistentCache, filesForDeletion);
            }
        }
    }

    protected abstract List<File> findFilesToDelete(final PersistentCache persistentCache, File[] filesEligibleForCleanup);

    static File[] findEligibleFiles(final PersistentCache persistentCache) {
        // TODO: This doesn't descend subdirectories.
        return persistentCache.getBaseDir().listFiles(new FileFilter() {
            @Override
            public boolean accept(File file) {
                return !isReserved(persistentCache, file);
            }
        });
    }

    static boolean isReserved(final PersistentCache persistentCache, File file) {
        return persistentCache.getReservedCacheFiles().contains(file);
    }

    @VisibleForTesting
    static void cleanupFiles(final PersistentCache persistentCache, final List<File> filesForDeletion) {
        // Need to remove some files
        long removedSize = deleteFiles(filesForDeletion);
        LOGGER.info("{} removing {} cache entries ({} reclaimed).", persistentCache, filesForDeletion.size(), FileUtils.byteCountToDisplaySize(removedSize));
    }

    private static long deleteFiles(List<File> files) {
        long removedSize = 0;
        for (File file : files) {
            try {
                long size = file.length();
                if (GFileUtils.deleteQuietly(file)) {
                    removedSize += size;
                }
            } catch (Exception e) {
                LOGGER.debug("Could not clean up cache " + file, e);
            }
        }
        return removedSize;
    }
}
