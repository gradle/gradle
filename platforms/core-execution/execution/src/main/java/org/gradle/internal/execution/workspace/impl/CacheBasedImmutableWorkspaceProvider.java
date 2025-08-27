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
import org.gradle.cache.internal.ProducerGuard;
import org.gradle.internal.execution.workspace.ImmutableWorkspaceProvider;
import org.gradle.internal.file.FileAccessTimeJournal;
import org.gradle.internal.file.impl.SingleDepthFileAccessTracker;
import org.jspecify.annotations.Nullable;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.function.Supplier;

import static org.gradle.cache.FineGrainedCacheCleanupStrategy.FineGrainedCacheDeleter;

public class CacheBasedImmutableWorkspaceProvider implements ImmutableWorkspaceProvider, Closeable {
    private static final int DEFAULT_FILE_TREE_DEPTH_TO_TRACK_AND_CLEANUP = 1;

    private final SingleDepthFileAccessTracker fileAccessTracker;
    private final File baseDirectory;
    private final FineGrainedPersistentCache cache;
    private final FineGrainedCacheDeleter deleter;
    private final ProducerGuard<String> guard;

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
        this.cache = cacheBuilder
            .withLeastRecentCleanup(cacheCleanupStrategy)
            .open();
        this.deleter = cacheCleanupStrategy.getCacheDeleter(cache);
        this.baseDirectory = cache.getBaseDir();
        this.guard = ProducerGuard.adaptive();
        this.fileAccessTracker = new SingleDepthFileAccessTracker(fileAccessTimeJournal, baseDirectory, treeDepthToTrackAndCleanup);
    }

    @Override
    public LockingImmutableWorkspace getLockingWorkspace(String path) {
        File workspace = new File(baseDirectory, path);
        File completeMarker = new File(workspace, "gradle.complete");
        fileAccessTracker.markAccessed(workspace);
        return new LockingImmutableWorkspace() {

            @Nullable
            private Boolean isStale;

            @Override
            public File getImmutableLocation() {
                return workspace;
            }

            @Override
            public <T> T withProcessLock(Supplier<T> supplier) {
                return cache.useCache(path, supplier);
            }

            @Override
            public <T> T withThreadLock(Supplier<T> supplier) {
                return guard.guardByKey(path, supplier);
            }

            @Override
            public boolean isStale() {
                if (isStale == null) {
                    isStale = deleter.isStale(workspace);
                }
                return isStale;
            }

            @Override
            public void unstale() {
                deleter.unstale(workspace);
                isStale = false;
            }

            @Override
            public boolean deleteStaleFiles() {
                return deleter.delete(workspace);
            }

            @Override
            public boolean isMarkedComplete() {
                return completeMarker.exists();
            }

            @Override
            public void markCompleted() {
                try {
                    completeMarker.createNewFile();
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }

            @Override
            public void withDeletionLock(Runnable supplier) {
                deleter.withDeletionLock(workspace, supplier);
            }
        };
    }

    @Override
    public void close() {
        cache.close();
    }
}
