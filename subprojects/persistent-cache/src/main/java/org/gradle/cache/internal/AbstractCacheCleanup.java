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
import org.gradle.cache.CleanableStore;
import org.gradle.cache.CleanupAction;
import org.gradle.util.GFileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public abstract class AbstractCacheCleanup implements CleanupAction {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractCacheCleanup.class);

    public static final EligibleFilesFinder DIRECT_CHILDREN = new EligibleFilesFinder() {
        @Override
        public File[] find(File baseDir, FileFilter filter) {
            return baseDir.listFiles(filter);
        }
    };

    public static final EligibleFilesFinder SECOND_LEVEL_CHILDREN = new EligibleFilesFinder() {
        @Override
        public File[] find(File baseDir, FileFilter filter) {
            List<File> result = new ArrayList<File>();
            for (File fileInBaseDir : listFiles(baseDir, filter)) {
                if (fileInBaseDir.isDirectory()) {
                    result.addAll(listFiles(fileInBaseDir, filter));
                }
            }
            return result.toArray(new File[0]);
        }

        private List<File> listFiles(File baseDir, FileFilter filter) {
            File[] files = baseDir.listFiles(filter);
            return files == null ? Collections.<File>emptyList() : Arrays.asList(files);
        }
    };

    private final EligibleFilesFinder eligibleFilesFinder;

    public AbstractCacheCleanup(EligibleFilesFinder eligibleFilesFinder) {
        this.eligibleFilesFinder = eligibleFilesFinder;
    }

    @Override
    public final void clean(CleanableStore cleanableStore) {
        File[] filesEligibleForCleanup = findEligibleFiles(cleanableStore);

        if (filesEligibleForCleanup.length > 0) {
            List<File> filesForDeletion = findFilesToDelete(cleanableStore, filesEligibleForCleanup);

            if (!filesForDeletion.isEmpty()) {
                cleanupFiles(cleanableStore, filesForDeletion);
            }
        }
    }

    protected abstract List<File> findFilesToDelete(CleanableStore cleanableStore, File[] filesEligibleForCleanup);

    protected File[] findEligibleFiles(CleanableStore cleanableStore) {
        return eligibleFilesFinder.find(cleanableStore.getBaseDir(), new NonReservedCacheFileFilter(cleanableStore));
    }

    @VisibleForTesting
    static void cleanupFiles(CleanableStore cleanableStore, List<File> filesForDeletion) {
        // Need to remove some files
        long removedSize = deleteFiles(filesForDeletion);
        LOGGER.info("{} removing {} cache entries ({} reclaimed).", cleanableStore.getDisplayName(), filesForDeletion.size(), FileUtils.byteCountToDisplaySize(removedSize));
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

    public interface EligibleFilesFinder {
        File[] find(File baseDir, FileFilter filter);
    }
}
