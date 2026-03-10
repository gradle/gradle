/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.internal.execution.steps;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedMap;
import org.gradle.caching.BuildCacheKey;
import org.gradle.caching.internal.CacheableEntity;
import org.gradle.caching.internal.controller.BuildCacheController;
import org.gradle.caching.internal.controller.service.BuildCacheLoadResult;
import org.gradle.caching.internal.origin.OriginMetadata;
import org.gradle.internal.Try;
import org.gradle.internal.execution.Execution;
import org.gradle.internal.execution.OutputChangeListener;
import org.gradle.internal.execution.OutputVisitor;
import org.gradle.internal.execution.UnitOfWork;
import org.gradle.internal.execution.history.ExecutionOutputState;
import org.gradle.internal.execution.history.impl.DefaultExecutionOutputState;
import org.gradle.internal.file.Deleter;
import org.gradle.internal.file.TreeType;
import org.gradle.internal.snapshot.FileSystemSnapshot;
import org.gradle.internal.vfs.FileSystemAccess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.gradle.internal.execution.Execution.ExecutionOutcome.FROM_CACHE;

/**
 * A build cache step for immutable work that races remote cache download against execution.
 *
 * For immutable work items (artifact transforms, build script compilation), the workspace is
 * content-addressable and both execution and cache restore produce identical outputs. This means
 * we can safely start execution without waiting for the remote cache response.
 *
 * The flow is: check local cache synchronously, and on a local miss start a remote download
 * in the background while executing immediately on the current thread. If the remote download
 * completes with a hit before execution finishes, the execution thread is interrupted and the
 * cached result is used instead. If execution finishes first, the remote download is cancelled.
 */
public class OptimisticBuildCacheStep<C extends WorkspaceContext & CachingContext> implements Step<C, AfterExecutionResult> {
    private static final Logger LOGGER = LoggerFactory.getLogger(OptimisticBuildCacheStep.class);

    private final BuildCacheController buildCache;
    private final Deleter deleter;
    private final FileSystemAccess fileSystemAccess;
    private final OutputChangeListener outputChangeListener;
    private final Step<? super C, ? extends AfterExecutionResult> delegate;

    public OptimisticBuildCacheStep(
        BuildCacheController buildCache,
        Deleter deleter,
        FileSystemAccess fileSystemAccess,
        OutputChangeListener outputChangeListener,
        Step<? super C, ? extends AfterExecutionResult> delegate
    ) {
        this.buildCache = buildCache;
        this.deleter = deleter;
        this.fileSystemAccess = fileSystemAccess;
        this.outputChangeListener = outputChangeListener;
        this.delegate = delegate;
    }

    @Override
    public AfterExecutionResult execute(UnitOfWork work, C context) {
        return context.getCachingState().fold(
            cachingEnabled -> executeWithCache(work, context, cachingEnabled.getCacheKeyCalculatedState().getKey()),
            cachingDisabled -> executeWithoutCache(work, context)
        );
    }

    private AfterExecutionResult executeWithCache(UnitOfWork work, C context, BuildCacheKey cacheKey) {
        CacheableWork cacheableWork = new CacheableWork(context.getIdentity().getUniqueId(), context.getWorkspace(), work);

        // 1. Check local cache synchronously (fast)
        Optional<BuildCacheLoadResult> localResult;
        try {
            localResult = work.isAllowedToLoadFromCache()
                ? tryLoadingFromLocalCache(cacheKey, cacheableWork)
                : Optional.empty();
        } catch (Exception e) {
            return new AfterExecutionResult(
                Duration.ZERO,
                Try.failure(new RuntimeException(
                    String.format("Failed to load cache entry %s for %s: %s",
                        cacheKey.getHashCode(),
                        work.getDisplayName(),
                        e.getMessage()
                    ),
                    e
                )),
                null
            );
        }

        if (localResult.isPresent()) {
            BuildCacheLoadResult cacheHit = localResult.get();
            if (LOGGER.isInfoEnabled()) {
                LOGGER.info("Loaded cache entry for {} with cache key {} from local cache",
                    work.getDisplayName(), cacheKey.getHashCode());
            }
            cleanLocalState(context.getWorkspace(), work);
            return buildCacheResult(cacheHit, work);
        }

        // 2. Local miss — start remote download and execute optimistically
        if (work.isAllowedToLoadFromCache()) {
            return executeOptimistically(work, context, cacheKey, cacheableWork);
        } else {
            return executeAndStore(work, context, cacheKey, cacheableWork);
        }
    }

    private AfterExecutionResult executeOptimistically(UnitOfWork work, C context, BuildCacheKey cacheKey, CacheableWork cacheableWork) {
        // Start remote download (download only, no unpack)
        CompletableFuture<File> downloadFuture = buildCache.downloadRemoteAsync(cacheKey);

        // Track if the remote download completed with a hit and set up interrupt
        AtomicReference<File> downloadedFile = new AtomicReference<>();
        Thread executionThread = Thread.currentThread();
        @SuppressWarnings("FutureReturnValueIgnored")
        CompletableFuture<Void> ignored = downloadFuture.thenAccept(file -> {
            if (file != null && downloadedFile.compareAndSet(null, file)) {
                LOGGER.info("Remote cache hit for {} arrived during execution, interrupting",
                    work.getDisplayName());
                executionThread.interrupt();
            }
        });

        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("Local cache miss for {} with cache key {}, executing optimistically",
                work.getDisplayName(), cacheKey.getHashCode());
        }

        // Execute on current thread — may be interrupted by cache hit
        AfterExecutionResult result;
        try {
            result = executeWithoutCache(work, context);
        } finally {
            // Prevent further interrupts from the download future
            downloadFuture.cancel(true);
            // Clear any pending interrupt flag (race: download may have set it just as we finished)
            Thread.interrupted();
        }

        // Check if execution was interrupted by a remote cache hit
        File tempFile = downloadedFile.get();
        if (tempFile != null) {
            if (!result.getExecution().isSuccessful()) {
                // Execution failed (likely due to our interrupt) — use the cached result
                try {
                    LOGGER.info("Using remote cache result for {} after interrupting execution", work.getDisplayName());
                    // Invalidate VFS for the workspace to clear any partial execution snapshots
                    Collection<String> locations = work.getAllOutputLocationsForInvalidation(context.getWorkspace());
                    fileSystemAccess.invalidate(locations);
                    // Unpack the downloaded cache entry into the workspace
                    Optional<BuildCacheLoadResult> cacheResult = buildCache.loadFromDownloadedRemoteEntry(cacheKey, cacheableWork, tempFile);
                    if (cacheResult.isPresent()) {
                        cleanLocalState(context.getWorkspace(), work);
                        return buildCacheResult(cacheResult.get(), work);
                    }
                } catch (Exception e) {
                    LOGGER.warn("Failed to load from downloaded cache entry for {}, falling through to execution failure",
                        work.getDisplayName(), e);
                } finally {
                    tempFile.delete();
                }
                // Cache unpack failed — return the original (failed) execution result
                return result;
            } else {
                // Execution succeeded despite the interrupt — use execution result, clean up temp file
                tempFile.delete();
            }
        }

        // Execution completed normally — store in cache
        try {
            result.getExecution().ifSuccessfulOrElse(
                executionResult -> storeInCacheUnlessDisabled(cacheableWork, cacheKey, result, executionResult),
                failure -> LOGGER.debug("Not storing result of {} in cache because the execution failed", work.getDisplayName())
            );
        } catch (Exception storeFailure) {
            return new AfterExecutionResult(Result.failed(storeFailure, result.getDuration()), result.getAfterExecutionOutputState().orElse(null));
        }

        return result;
    }

    private AfterExecutionResult executeAndStore(UnitOfWork work, C context, BuildCacheKey cacheKey, CacheableWork cacheableWork) {
        AfterExecutionResult result = executeWithoutCache(work, context);
        try {
            result.getExecution().ifSuccessfulOrElse(
                executionResult -> storeInCacheUnlessDisabled(cacheableWork, cacheKey, result, executionResult),
                failure -> LOGGER.debug("Not storing result of {} in cache because the execution failed", work.getDisplayName())
            );
        } catch (Exception storeFailure) {
            return new AfterExecutionResult(Result.failed(storeFailure, result.getDuration()), result.getAfterExecutionOutputState().orElse(null));
        }
        return result;
    }

    private Optional<BuildCacheLoadResult> tryLoadingFromLocalCache(BuildCacheKey cacheKey, CacheableWork cacheableWork) {
        Collection<String> cacheableLocations = cacheableWork.work.getCachedOutputLocationsForInvalidation(cacheableWork.workspace);
        fileSystemAccess.invalidate(cacheableLocations);
        return buildCache.loadLocally(cacheKey, cacheableWork);
    }

    private AfterExecutionResult buildCacheResult(BuildCacheLoadResult cacheHit, UnitOfWork work) {
        OriginMetadata originMetadata = cacheHit.getOriginMetadata();
        Try<Execution> execution = Try.successful(Execution.skipped(FROM_CACHE, work));
        ExecutionOutputState afterExecutionOutputState = new DefaultExecutionOutputState(true, cacheHit.getResultingSnapshots(), originMetadata, true);
        afterExecutionOutputState.getOutputFilesProducedByWork().values().stream()
            .flatMap(FileSystemSnapshot::roots)
            .forEach(fileSystemAccess::record);
        return new AfterExecutionResult(originMetadata.getExecutionTime(), execution, afterExecutionOutputState);
    }

    private void cleanLocalState(File workspace, UnitOfWork work) {
        work.visitOutputs(workspace, new OutputVisitor() {
            @Override
            public void visitLocalState(File localStateRoot) {
                try {
                    outputChangeListener.invalidateCachesFor(ImmutableList.of(localStateRoot.getAbsolutePath()));
                    deleter.deleteRecursively(localStateRoot);
                } catch (IOException ex) {
                    throw new UncheckedIOException(String.format("Failed to clean up local state files for %s: %s", work.getDisplayName(), localStateRoot), ex);
                }
            }
        });
    }

    private void storeInCacheUnlessDisabled(CacheableWork cacheableWork, BuildCacheKey cacheKey, AfterExecutionResult result, Execution executionResult) {
        if (executionResult.canStoreOutputsInCache()) {
            result.getAfterExecutionOutputState()
                .ifPresent(afterExecutionState -> store(cacheableWork, cacheKey, afterExecutionState.getOutputFilesProducedByWork(), afterExecutionState.getOriginMetadata().getExecutionTime()));
        } else {
            LOGGER.debug("Not storing result of {} in cache because storing was disabled for this execution", cacheableWork.getDisplayName());
        }
    }

    private void store(CacheableWork work, BuildCacheKey cacheKey, ImmutableSortedMap<String, FileSystemSnapshot> outputFilesProducedByWork, Duration executionTime) {
        try {
            buildCache.store(cacheKey, work, outputFilesProducedByWork, executionTime);
            if (LOGGER.isInfoEnabled()) {
                LOGGER.info("Stored cache entry for {} with cache key {}",
                    work.getDisplayName(), cacheKey.getHashCode());
            }
        } catch (Exception e) {
            throw new RuntimeException(
                String.format("Failed to store cache entry %s for %s: %s",
                    cacheKey.getHashCode(),
                    work.getDisplayName(),
                    e.getMessage()),
                e);
        }
    }

    private AfterExecutionResult executeWithoutCache(UnitOfWork work, C context) {
        return delegate.execute(work, context);
    }

    private static class CacheableWork implements CacheableEntity {
        private final String identity;
        private final File workspace;
        final UnitOfWork work;

        public CacheableWork(String identity, File workspace, UnitOfWork work) {
            this.identity = identity;
            this.workspace = workspace;
            this.work = work;
        }

        @Override
        public String getIdentity() {
            return identity;
        }

        @Override
        public Class<?> getType() {
            return work.getClass();
        }

        @Override
        public String getDisplayName() {
            return work.getDisplayName();
        }

        @Override
        public void visitOutputTrees(CacheableTreeVisitor visitor) {
            work.visitOutputs(workspace, new OutputVisitor() {
                @Override
                public void visitOutputProperty(String propertyName, TreeType type, OutputFileValueSupplier value) {
                    visitor.visitOutputTree(propertyName, type, value.getValue());
                }
            });
        }
    }
}
