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

import org.apache.commons.io.FileUtils;
import org.gradle.cache.CleanableStore;
import org.gradle.cache.CleanupAction;
import org.gradle.cache.CleanupProgressMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

public abstract class AbstractCacheCleanup implements CleanupAction {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractCacheCleanup.class);

    private final FilesFinder eligibleFilesFinder;

    public AbstractCacheCleanup(FilesFinder eligibleFilesFinder) {
        this.eligibleFilesFinder = eligibleFilesFinder;
    }

    @Override
    public void clean(CleanableStore cleanableStore, CleanupProgressMonitor progressMonitor) {
        int filesDeleted = 0;
        for (File file : findEligibleFiles(cleanableStore)) {
            if (shouldDelete(file)) {
                progressMonitor.incrementDeleted();
                if (FileUtils.deleteQuietly(file)) {
                    handleDeletion(file);
                    filesDeleted += 1 + deleteEmptyParentDirectories(cleanableStore.getBaseDir(), file.getParentFile());
                }
            } else {
                progressMonitor.incrementSkipped();
            }
        }
        LOGGER.debug("{} cleanup deleted {} files/directories.", cleanableStore.getDisplayName(), filesDeleted);
    }

    protected int deleteEmptyParentDirectories(File baseDir, File dir) {
        if (dir.equals(baseDir)) {
            return 0;
        }
        File[] files = dir.listFiles();
        if (files != null && files.length == 0 && dir.delete()) {
            handleDeletion(dir);
            return 1 + deleteEmptyParentDirectories(baseDir, dir.getParentFile());
        }
        return 0;
    }

    protected abstract boolean shouldDelete(File file);

    protected abstract void handleDeletion(File file);

    private Iterable<File> findEligibleFiles(CleanableStore cleanableStore) {
        return eligibleFilesFinder.find(cleanableStore.getBaseDir(), new NonReservedFileFilter(cleanableStore.getReservedCacheFiles()));
    }

}
