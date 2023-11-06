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

import com.google.common.io.Closer;
import org.gradle.api.internal.cache.CacheConfigurationsInternal;
import org.gradle.cache.CacheBuilder;
import org.gradle.cache.CacheCleanupStrategy;
import org.gradle.cache.CleanupAction;
import org.gradle.cache.DefaultCacheCleanupStrategy;
import org.gradle.cache.PersistentCache;
import org.gradle.cache.UnscopedCacheBuilderFactory;
import org.gradle.cache.internal.LeastRecentlyUsedCacheCleanup;
import org.gradle.cache.internal.SingleDepthFilesFinder;
import org.gradle.internal.execution.history.ExecutionHistoryStore;
import org.gradle.internal.execution.workspace.WorkspaceProvider;
import org.gradle.internal.file.FileAccessTimeJournal;
import org.gradle.internal.file.impl.SingleDepthFileAccessTracker;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import static org.gradle.cache.FileLockManager.LockMode.OnDemand;
import static org.gradle.cache.internal.filelock.LockOptionsBuilder.mode;

public class FinerGrainedImmutableWorkspaceProvider implements WorkspaceProvider, Closeable {
    private static final int DEFAULT_FILE_TREE_DEPTH_TO_TRACK_AND_CLEANUP = 1;

    private final SingleDepthFileAccessTracker fileAccessTracker;
    private final File baseDirectory;
    private final ExecutionHistoryStore executionHistoryStore;
    private final PersistentCache cache;
    private final Map<String, PersistentCache> finerGrainedCaches;
    private final Function<String, CacheBuilder> finerCacheFactory;

    private FinerGrainedImmutableWorkspaceProvider(
        CacheBuilder cacheBuilder,
        Function<String, CacheBuilder> finerCacheFactory,
        FileAccessTimeJournal fileAccessTimeJournal,
        ExecutionHistoryStore historyFactory,
        CacheConfigurationsInternal cacheConfigurations
    ) {
        PersistentCache cache = cacheBuilder
            .withCleanupStrategy(createCacheCleanupStrategy(fileAccessTimeJournal, DEFAULT_FILE_TREE_DEPTH_TO_TRACK_AND_CLEANUP, cacheConfigurations))
            .withLockOptions(mode(OnDemand))
            .open();
        this.finerCacheFactory = finerCacheFactory;
        this.finerGrainedCaches = new ConcurrentHashMap<>();
        this.cache = cache;
        this.baseDirectory = cache.getBaseDir();
        this.fileAccessTracker = new SingleDepthFileAccessTracker(fileAccessTimeJournal, baseDirectory, DEFAULT_FILE_TREE_DEPTH_TO_TRACK_AND_CLEANUP);
        this.executionHistoryStore = historyFactory;
    }

    private static CacheCleanupStrategy createCacheCleanupStrategy(FileAccessTimeJournal fileAccessTimeJournal, int treeDepthToTrackAndCleanup, CacheConfigurationsInternal cacheConfigurations) {
        return DefaultCacheCleanupStrategy.from(
            createCleanupAction(fileAccessTimeJournal, treeDepthToTrackAndCleanup, cacheConfigurations),
            cacheConfigurations.getCleanupFrequency()::get
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

    private PersistentCache openFinnerCache(String path) {
        return finerCacheFactory.apply(path)
            .withLockOptions(mode(OnDemand))
            .open();
    }

    @Override
    public void close() {
        try (Closer closer = Closer.create()) {
            finerGrainedCaches.values().forEach(closer::register);
            closer.register(cache);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static FinerGrainedImmutableWorkspaceProvider withExternalHistory(
        String cacheDisplayName,
        File cacheBaseDir,
        UnscopedCacheBuilderFactory cacheFactory,
        FileAccessTimeJournal fileAccessTimeJournal,
        ExecutionHistoryStore executionHistoryStore,
        CacheConfigurationsInternal cacheConfigurations
    ) {
        CacheBuilder cacheBuilder = cacheFactory.cache(cacheBaseDir)
            .withDisplayName(cacheDisplayName)
            .withCrossVersionCache(CacheBuilder.LockTarget.DefaultTarget);
        Function<String, CacheBuilder> finerCacheFactory = path -> cacheFactory.cache(new File(cacheBaseDir, path))
            .withDisplayName(cacheDisplayName + " for " + path)
            .withCrossVersionCache(CacheBuilder.LockTarget.DefaultTarget);
        return new FinerGrainedImmutableWorkspaceProvider(
            cacheBuilder,
            finerCacheFactory,
            fileAccessTimeJournal,
            executionHistoryStore,
            cacheConfigurations
        );
    }
}
