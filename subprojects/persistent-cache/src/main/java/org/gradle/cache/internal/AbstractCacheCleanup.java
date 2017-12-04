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
import org.gradle.api.Action;
import org.gradle.cache.PersistentCache;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.CallableBuildOperation;
import org.gradle.internal.operations.RunnableBuildOperation;
import org.gradle.internal.progress.BuildOperationDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FilenameFilter;
import java.util.List;

abstract class AbstractCacheCleanup implements Action<PersistentCache> {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractCacheCleanup.class);

    private final BuildOperationExecutor buildOperationExecutor;

    public AbstractCacheCleanup(BuildOperationExecutor buildOperationExecutor) {
        this.buildOperationExecutor = buildOperationExecutor;
    }

    @Override
    public void execute(final PersistentCache persistentCache) {
        buildOperationExecutor.run(new RunnableBuildOperation() {
            @Override
            public void run(BuildOperationContext context) {
                cleanup(persistentCache);
            }

            @Override
            public BuildOperationDescriptor.Builder description() {
                return BuildOperationDescriptor.displayName("Clean up " + persistentCache);
            }
        });

    }

    private void cleanup(final PersistentCache persistentCache) {
        final File[] filesEligibleForCleanup = buildOperationExecutor.call(new CallableBuildOperation<File[]>() {
            @Override
            public File[] call(BuildOperationContext context) {
                return findEligibleFiles(persistentCache.getBaseDir());
            }

            @Override
            public BuildOperationDescriptor.Builder description() {
                return BuildOperationDescriptor.displayName("Scan " + persistentCache.getBaseDir());
            }
        });

        if (filesEligibleForCleanup.length > 0) {
            final List<File> filesForDeletion = buildOperationExecutor.call(new CallableBuildOperation<List<File>>() {
                @Override
                public List<File> call(BuildOperationContext context) {
                    return findFilesToDelete(persistentCache, filesEligibleForCleanup);
                }

                @Override
                public BuildOperationDescriptor.Builder description() {
                    return BuildOperationDescriptor.displayName("Choose files to delete from " + persistentCache);
                }
            });

            if (!filesForDeletion.isEmpty()) {
                buildOperationExecutor.run(new RunnableBuildOperation() {
                    @Override
                    public void run(BuildOperationContext context) {
                        cleanupFiles(persistentCache, filesForDeletion);
                    }

                    @Override
                    public BuildOperationDescriptor.Builder description() {
                        return BuildOperationDescriptor.displayName("Delete files for " + persistentCache);
                    }
                });
            }
        }
    }

    protected abstract List<File> findFilesToDelete(final PersistentCache persistentCache, File[] filesEligibleForCleanup);

    File[] findEligibleFiles(File cacheDir) {
        // TODO: This doesn't descend subdirectories.
        return cacheDir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return canBeDeleted(name);
            }
        });
    }

    protected boolean canBeDeleted(String name) {
        return !(name.endsWith(".properties") || name.endsWith(".lock"));
    }

    void cleanupFiles(final PersistentCache persistentCache, final List<File> filesForDeletion) {
        // Need to remove some files
        long removedSize = deleteFiles(filesForDeletion);
        LOGGER.info("{} removing {} cache entries ({} reclaimed).", persistentCache, filesForDeletion.size(), FileUtils.byteCountToDisplaySize(removedSize));
    }

    private long deleteFiles(List<File> files) {
        long removedSize = 0;
        for (File file : files) {
            try {
                long size = file.length();
                if (file.delete()) {
                    removedSize += size;
                }
            } catch (Exception e) {
                LOGGER.debug("Could not clean up cache " + file, e);
            }
        }
        return removedSize;
    }
}
