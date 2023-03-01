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
import org.gradle.api.internal.cache.DefaultCacheCleanupStrategy;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.cache.CacheBuilder;
import org.gradle.cache.CacheCleanupStrategy;
import org.gradle.cache.CleanupAction;
import org.gradle.cache.FileLockManager;
import org.gradle.cache.PersistentCache;
import org.gradle.cache.internal.InMemoryCacheDecoratorFactory;
import org.gradle.cache.internal.LeastRecentlyUsedCacheCleanup;
import org.gradle.cache.internal.SingleDepthFilesFinder;
import org.gradle.internal.execution.history.ExecutionHistoryStore;
import org.gradle.internal.execution.history.impl.DefaultExecutionHistoryStore;
import org.gradle.internal.execution.workspace.WorkspaceProvider;
import org.gradle.internal.file.FileAccessTimeJournal;
import org.gradle.internal.file.impl.SingleDepthFileAccessTracker;
import org.gradle.internal.hash.ClassLoaderHierarchyHasher;

import java.io.Closeable;
import java.io.File;
import java.util.function.Function;

import static org.gradle.cache.internal.filelock.LockOptionsBuilder.mode;

public class DefaultImmutableWorkspaceProvider implements WorkspaceProvider, Closeable {
    private static final int DEFAULT_FILE_TREE_DEPTH_TO_TRACK_AND_CLEANUP = 1;

    private final SingleDepthFileAccessTracker fileAccessTracker;
    private final File baseDirectory;
    private final ExecutionHistoryStore executionHistoryStore;
    private final PersistentCache cache;

    public static DefaultImmutableWorkspaceProvider withBuiltInHistory(
        CacheBuilder cacheBuilder,
        FileAccessTimeJournal fileAccessTimeJournal,
        InMemoryCacheDecoratorFactory inMemoryCacheDecoratorFactory,
        StringInterner stringInterner,
        ClassLoaderHierarchyHasher classLoaderHasher,
        CacheConfigurationsInternal cacheConfigurations
    ) {
        return withBuiltInHistory(
            cacheBuilder,
            fileAccessTimeJournal,
            inMemoryCacheDecoratorFactory,
            stringInterner,
            classLoaderHasher,
            DEFAULT_FILE_TREE_DEPTH_TO_TRACK_AND_CLEANUP,
            cacheConfigurations
        );
    }

    public static DefaultImmutableWorkspaceProvider withBuiltInHistory(
        CacheBuilder cacheBuilder,
        FileAccessTimeJournal fileAccessTimeJournal,
        InMemoryCacheDecoratorFactory inMemoryCacheDecoratorFactory,
        StringInterner stringInterner,
        ClassLoaderHierarchyHasher classLoaderHasher,
        int treeDepthToTrackAndCleanup,
        CacheConfigurationsInternal cacheConfigurations
    ) {
        return new DefaultImmutableWorkspaceProvider(
            cacheBuilder,
            fileAccessTimeJournal,
            cache -> new DefaultExecutionHistoryStore(() -> cache, inMemoryCacheDecoratorFactory, stringInterner, classLoaderHasher),
            treeDepthToTrackAndCleanup,
            cacheConfigurations
        );
    }

    public static DefaultImmutableWorkspaceProvider withExternalHistory(
        CacheBuilder cacheBuilder,
        FileAccessTimeJournal fileAccessTimeJournal,
        ExecutionHistoryStore executionHistoryStore,
        CacheConfigurationsInternal cacheConfigurations
    ) {
        return new DefaultImmutableWorkspaceProvider(
            cacheBuilder,
            fileAccessTimeJournal,
            __ -> executionHistoryStore,
            DEFAULT_FILE_TREE_DEPTH_TO_TRACK_AND_CLEANUP,
            cacheConfigurations
        );
    }

    private DefaultImmutableWorkspaceProvider(
        CacheBuilder cacheBuilder,
        FileAccessTimeJournal fileAccessTimeJournal,
        Function<PersistentCache, ExecutionHistoryStore> historyFactory,
        int treeDepthToTrackAndCleanup,
        CacheConfigurationsInternal cacheConfigurations
    ) {
        PersistentCache cache = cacheBuilder
            .withCleanupStrategy(createCacheCleanupStrategy(fileAccessTimeJournal, treeDepthToTrackAndCleanup, cacheConfigurations))
            .withLockOptions(mode(FileLockManager.LockMode.OnDemand)) // Lock on demand
            .open();
        this.cache = cache;
        this.baseDirectory = cache.getBaseDir();
        this.fileAccessTracker = new SingleDepthFileAccessTracker(fileAccessTimeJournal, baseDirectory, treeDepthToTrackAndCleanup);
        this.executionHistoryStore = historyFactory.apply(cache);
    }

    private CacheCleanupStrategy createCacheCleanupStrategy(FileAccessTimeJournal fileAccessTimeJournal, int treeDepthToTrackAndCleanup, CacheConfigurationsInternal cacheConfigurations) {
        return DefaultCacheCleanupStrategy.from(
            createCleanupAction(fileAccessTimeJournal, treeDepthToTrackAndCleanup, cacheConfigurations),
            cacheConfigurations.getCleanupFrequency()
        );
    }

    private static CleanupAction createCleanupAction(FileAccessTimeJournal fileAccessTimeJournal, int treeDepthToTrackAndCleanup, CacheConfigurationsInternal cacheConfigurations) {
        return new LeastRecentlyUsedCacheCleanup(
            new SingleDepthFilesFinder(treeDepthToTrackAndCleanup),
            fileAccessTimeJournal,
            cacheConfigurations.getCreatedResources().getRemoveUnusedEntriesOlderThanAsSupplier()
        );
    }

    @Override
    public <T> T withWorkspace(String path, WorkspaceAction<T> action) {
        return cache.withFileLock(() -> {
            File workspace = new File(baseDirectory, path);
            fileAccessTracker.markAccessed(workspace);
            return action.executeInWorkspace(workspace, executionHistoryStore);
        });
    }

    @Override
    public void close() {
        cache.close();
    }
}
