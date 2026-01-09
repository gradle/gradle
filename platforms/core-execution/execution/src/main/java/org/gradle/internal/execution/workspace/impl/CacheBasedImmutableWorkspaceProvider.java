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
import org.gradle.cache.FineGrainedCacheCleanupStrategyFactory;
import org.gradle.cache.FineGrainedMarkAndSweepCacheCleanupStrategy;
import org.gradle.cache.FineGrainedMarkAndSweepCacheCleanupStrategy.FineGrainedCacheEntrySoftDeleter;
import org.gradle.cache.FineGrainedPersistentCache;
import org.gradle.internal.Cast;
import org.gradle.internal.execution.workspace.ImmutableWorkspaceProvider;
import org.gradle.internal.file.FileAccessTimeJournal;
import org.gradle.internal.file.impl.SingleDepthFileAccessTracker;

import java.io.Closeable;
import java.io.File;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import java.util.function.Supplier;

public class CacheBasedImmutableWorkspaceProvider implements ImmutableWorkspaceProvider, Closeable {

    private final SingleDepthFileAccessTracker fileAccessTracker;
    private final FineGrainedPersistentCache cache;
    private final FineGrainedCacheEntrySoftDeleter softDeleter;
    private final ConcurrentMap<String, CompletableFuture<?>> workspaceResults;

    public static CacheBasedImmutableWorkspaceProvider createWorkspaceProvider(
        FineGrainedCacheBuilder cacheBuilder,
        FileAccessTimeJournal fileAccessTimeJournal,
        CacheConfigurationsInternal cacheConfigurations,
        FineGrainedCacheCleanupStrategyFactory cacheCleanupStrategyFactory
    ) {
        return new CacheBasedImmutableWorkspaceProvider(
            cacheBuilder,
            fileAccessTimeJournal,
            cacheConfigurations,
            cacheCleanupStrategyFactory
        );
    }

    private CacheBasedImmutableWorkspaceProvider(
        FineGrainedCacheBuilder cacheBuilder,
        FileAccessTimeJournal fileAccessTimeJournal,
        CacheConfigurationsInternal cacheConfigurations,
        FineGrainedCacheCleanupStrategyFactory cacheCleanupStrategyFactory
    ) {
        FineGrainedMarkAndSweepCacheCleanupStrategy markAndSweepCleanupStrategy = cacheCleanupStrategyFactory.markAndSweepCleanupStrategy(
            cacheConfigurations.getCreatedResources().getEntryRetentionTimestampSupplier(),
            cacheConfigurations.getCleanupFrequency()::get
        );
        FineGrainedPersistentCache cache = cacheBuilder
            .withCleanupStrategy(markAndSweepCleanupStrategy)
            .open();
        this.softDeleter = markAndSweepCleanupStrategy.getSoftDeleter(cache);
        this.cache = cache;
        this.fileAccessTracker = new SingleDepthFileAccessTracker(fileAccessTimeJournal, cache.getBaseDir(), 1);
        this.workspaceResults = new ConcurrentHashMap<>();
    }

    @Override
    public ImmutableWorkspace getWorkspace(String path) {
        File workspace = new File(cache.getBaseDir(), path);
        fileAccessTracker.markAccessed(workspace);
        return new CachedBasedImmutableWorkspace(path, workspace, cache, softDeleter, workspaceResults);
    }

    @Override
    public void close() {
        cache.close();
    }

    public static class CachedBasedImmutableWorkspace implements ImmutableWorkspace {
        private final String entryKey;
        private final File workspace;
        private final FineGrainedPersistentCache cache;
        private final FineGrainedCacheEntrySoftDeleter softDeleter;
        private final Map<String, CompletableFuture<?>> workspaceResults;

        public CachedBasedImmutableWorkspace(
            String entryKey,
            File workspace,
            FineGrainedPersistentCache cache,
            FineGrainedCacheEntrySoftDeleter softDeleter,
            ConcurrentMap<String, CompletableFuture<?>> workspaceResults
        ) {
            this.entryKey = entryKey;
            this.workspace = workspace;
            this.cache = cache;
            this.softDeleter = softDeleter;
            this.workspaceResults = workspaceResults;
        }

        @Override
        public File getImmutableLocation() {
            return workspace;
        }

        @Override
        public <T> T withFileLock(Supplier<T> action) {
            return cache.withFileLock(entryKey, action);
        }

        @Override
        public <T> T getIfRunningOrCompute(Function<T, T> concurrentResultMapper, Supplier<T> action) {
            CompletableFuture<T> thisOperationFuture = new CompletableFuture<>();
            CompletableFuture<T> runningOperationFuture = Cast.uncheckedCast(workspaceResults.putIfAbsent(entryKey, thisOperationFuture));

            if (runningOperationFuture != null) {
                // If it's already running, wait for it to finish and map the result
                return concurrentResultMapper.apply(runningOperationFuture.join());
            }

            try {
                // Else run the action and pass a result to any thread that is waiting
                T result = action.get();
                thisOperationFuture.complete(result);
                return result;
            } catch (Exception e) {
                thisOperationFuture.completeExceptionally(e);
                throw e;
            } finally {
                workspaceResults.remove(entryKey);
            }
        }

        @Override
        public boolean isSoftDeleted() {
            return softDeleter.isSoftDeleted(entryKey);
        }

        @Override
        public void ensureUnSoftDeleted() {
            softDeleter.removeSoftDeleteMarker(entryKey);
        }
    }
}
