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
import org.gradle.cache.CacheBuilder;
import org.gradle.cache.CacheCleanupStrategyFactory;
import org.gradle.cache.CleanupAction;
import org.gradle.cache.FileLockManager;
import org.gradle.cache.PersistentCache;
import org.gradle.cache.internal.LeastRecentlyUsedCacheCleanup;
import org.gradle.cache.internal.SingleDepthFilesFinder;
import org.gradle.internal.execution.workspace.ImmutableWorkspaceProvider;
import org.gradle.internal.file.FileAccessTimeJournal;
import org.gradle.internal.file.impl.SingleDepthFileAccessTracker;

import java.io.Closeable;
import java.io.File;
import java.util.UUID;

public class CacheBasedImmutableWorkspaceProvider implements ImmutableWorkspaceProvider, Closeable {
    private static final int DEFAULT_FILE_TREE_DEPTH_TO_TRACK_AND_CLEANUP = 1;

    private final SingleDepthFileAccessTracker fileAccessTracker;
    private final File baseDirectory;
    private final PersistentCache cache;

    public static CacheBasedImmutableWorkspaceProvider createWorkspaceProvider(
        CacheBuilder cacheBuilder,
        FileAccessTimeJournal fileAccessTimeJournal,
        CacheConfigurationsInternal cacheConfigurations,
        CacheCleanupStrategyFactory cacheCleanupStrategyFactory
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
        CacheBuilder cacheBuilder,
        FileAccessTimeJournal fileAccessTimeJournal,
        int treeDepthToTrackAndCleanup,
        CacheConfigurationsInternal cacheConfigurations,
        CacheCleanupStrategyFactory cacheCleanupStrategyFactory
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
        CacheBuilder cacheBuilder,
        FileAccessTimeJournal fileAccessTimeJournal,
        int treeDepthToTrackAndCleanup,
        CacheConfigurationsInternal cacheConfigurations,
        CacheCleanupStrategyFactory cacheCleanupStrategyFactory
    ) {
        PersistentCache cache = cacheBuilder
            .withCleanupStrategy(cacheCleanupStrategyFactory.create(createCleanupAction(fileAccessTimeJournal, treeDepthToTrackAndCleanup, cacheConfigurations), cacheConfigurations.getCleanupFrequency()::get))
            // We don't need to lock the cache for immutable workspaces
            // as we are using unique temporary workspaces to run work in
            // and move them atomically into the cache
            // TODO Should use a read-write lock on the cache's base directory for cleanup, though
            .withInitialLockMode(FileLockManager.LockMode.None)
            .open();
        this.cache = cache;
        this.baseDirectory = cache.getBaseDir();
        this.fileAccessTracker = new SingleDepthFileAccessTracker(fileAccessTimeJournal, baseDirectory, treeDepthToTrackAndCleanup);
    }

    private static CleanupAction createCleanupAction(FileAccessTimeJournal fileAccessTimeJournal, int treeDepthToTrackAndCleanup, CacheConfigurationsInternal cacheConfigurations) {
        return new LeastRecentlyUsedCacheCleanup(
            new SingleDepthFilesFinder(treeDepthToTrackAndCleanup),
            fileAccessTimeJournal,
            cacheConfigurations.getCreatedResources().getEntryRetentionTimestampSupplier()
        );
    }

    @Override
    public ImmutableWorkspace getWorkspace(String path) {
        File immutableWorkspace = new File(baseDirectory, path);
        fileAccessTracker.markAccessed(immutableWorkspace);
        return new ImmutableWorkspace() {
            @Override
            public File getImmutableLocation() {
                return immutableWorkspace;
            }

            @Override
            public <T> T withTemporaryWorkspace(TemporaryWorkspaceAction<T> action) {
                // TODO Use Files.createTemporaryDirectory() instead
                String temporaryLocation = path + "-" + UUID.randomUUID();
                File temporaryWorkspace = new File(baseDirectory, temporaryLocation);
                return action.executeInTemporaryWorkspace(temporaryWorkspace);
            }
        };
    }

    @Override
    public void close() {
        cache.close();
    }
}
