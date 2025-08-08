/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.internal.execution.workspace.impl;

import org.gradle.api.internal.cache.CacheConfigurationsInternal;
import org.gradle.cache.FineGrainedCacheBuilder;
import org.gradle.cache.FineGrainedCacheCleanupStrategy;
import org.gradle.cache.FineGrainedCacheCleanupStrategyFactory;
import org.gradle.cache.FineGrainedPersistentCache;
import org.gradle.internal.execution.workspace.ImmutableWorkspaceProvider;
import org.gradle.internal.file.FileAccessTimeJournal;
import org.gradle.internal.file.impl.SingleDepthFileAccessTracker;

import java.io.Closeable;
import java.io.File;
import java.util.function.Supplier;

import static org.gradle.cache.FineGrainedCacheCleanupStrategy.FineGrainedCacheDeleter;

public class CacheBasedImmutableWorkspaceProvider implements ImmutableWorkspaceProvider, Closeable {
    private static final int DEFAULT_FILE_TREE_DEPTH_TO_TRACK_AND_CLEANUP = 1;

    private final SingleDepthFileAccessTracker fileAccessTracker;
    private final File baseDirectory;
    private final FineGrainedPersistentCache cache;
    private final FineGrainedCacheDeleter deleter;

    public static CacheBasedImmutableWorkspaceProvider createWorkspaceProvider(
        FineGrainedCacheBuilder cacheBuilder,
        FileAccessTimeJournal fileAccessTimeJournal,
        CacheConfigurationsInternal cacheConfigurations,
        FineGrainedCacheCleanupStrategyFactory cacheCleanupStrategyFactory
    ) {
        return createWorkspaceProvider(
            cacheBuilder,
            fileAccessTimeJournal,
            DEFAULT_FILE_TREE_DEPTH_TO_TRACK_AND_CLEANUP,
            cacheConfigurations,
            cacheCleanupStrategyFactory
        );
    }

    public static CacheBasedImmutableWorkspaceProvider createWorkspaceProvider(
        FineGrainedCacheBuilder cacheBuilder,
        FileAccessTimeJournal fileAccessTimeJournal,
        int treeDepthToTrackAndCleanup,
        CacheConfigurationsInternal cacheConfigurations,
        FineGrainedCacheCleanupStrategyFactory cacheCleanupStrategyFactory
    ) {
        return new CacheBasedImmutableWorkspaceProvider(
            cacheBuilder,
            fileAccessTimeJournal,
            treeDepthToTrackAndCleanup,
            cacheConfigurations,
            cacheCleanupStrategyFactory
        );
    }

    private CacheBasedImmutableWorkspaceProvider(
        FineGrainedCacheBuilder cacheBuilder,
        FileAccessTimeJournal fileAccessTimeJournal,
        int treeDepthToTrackAndCleanup,
        CacheConfigurationsInternal cacheConfigurations,
        FineGrainedCacheCleanupStrategyFactory cacheCleanupStrategyFactory
    ) {
        FineGrainedCacheCleanupStrategy cacheCleanupStrategy = cacheCleanupStrategyFactory.leastRecentlyUsedStrategy(
            treeDepthToTrackAndCleanup,
            cacheConfigurations.getCreatedResources().getEntryRetentionTimestampSupplier(),
            cacheConfigurations.getCleanupFrequency()::get
        );
        this.deleter = cacheCleanupStrategy.getCacheDeleter();
        this.cache = cacheBuilder
            .withLeastRecentCleanup(cacheCleanupStrategy)
            .open();
        this.baseDirectory = cache.getBaseDir();
        this.fileAccessTracker = new SingleDepthFileAccessTracker(fileAccessTimeJournal, baseDirectory, treeDepthToTrackAndCleanup);
    }

    @Override
    public LockingImmutableWorkspace getLockingWorkspace(String path) {
        File workspace = new File(baseDirectory, path);
        fileAccessTracker.markAccessed(workspace);
        return new LockingImmutableWorkspace() {

            @Override
            public File getImmutableLocation() {
                return workspace;
            }

            @Override
            public <T> T withWorkspaceLock(Supplier<T> supplier) {
                return cache.useCache(path, supplier);
            }

            @Override
            public boolean isStale() {
                return deleter.isStale(workspace);
            }

            @Override
            public void unstale() {
                deleter.unstale(workspace);
            }

            @Override
            public boolean deleteStaleFiles() {
                return deleter.delete(workspace);
            }
        };
    }

    @Override
    public void close() {
        cache.close();
    }
}
