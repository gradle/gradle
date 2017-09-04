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
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public final class FixedSizeOldestCacheCleanup implements Action<PersistentCache> {
    private static final Logger LOGGER = LoggerFactory.getLogger(FixedSizeOldestCacheCleanup.class);
    private static final Comparator<File> NEWEST_FIRST = Ordering.natural().onResultOf(new Function<File, Comparable<Long>>() {
        @Override
        public Comparable<Long> apply(File input) {
            return input.lastModified();
        }
    }).reverse();

    private final BuildOperationExecutor buildOperationExecutor;
    private final long targetSizeInMB;
    private final String partialFileSuffix;

    public FixedSizeOldestCacheCleanup(BuildOperationExecutor buildOperationExecutor, long targetSizeInMB, String partialFileSuffix) {
        this.buildOperationExecutor = buildOperationExecutor;
        this.targetSizeInMB = targetSizeInMB;
        this.partialFileSuffix = partialFileSuffix;
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

    List<File> findFilesToDelete(final PersistentCache persistentCache, File[] filesEligibleForCleanup) {
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

    File[] findEligibleFiles(File cacheDir) {
        // TODO: This doesn't descend subdirectories.
        return cacheDir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return canBeDeleted(name);
            }
        });
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

    boolean canBeDeleted(String name) {
        return !(name.endsWith(".properties") || name.endsWith(".lock") || name.endsWith(partialFileSuffix));
    }
}
