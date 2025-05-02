/*
 * Copyright 2018 the original author or authors.
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
import org.gradle.internal.execution.ExecutionEngine.Execution;
import org.gradle.internal.execution.MutableUnitOfWork;
import org.gradle.internal.execution.OutputChangeListener;
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
import java.util.Optional;

import static org.gradle.internal.execution.ExecutionEngine.ExecutionOutcome.FROM_CACHE;

public class BuildCacheStep<C extends WorkspaceContext & CachingContext> implements Step<C, AfterExecutionResult> {
    private static final Logger LOGGER = LoggerFactory.getLogger(BuildCacheStep.class);

    private final BuildCacheController buildCache;
    private final Deleter deleter;
    private final FileSystemAccess fileSystemAccess;
    private final OutputChangeListener outputChangeListener;
    private final Step<? super C, ? extends AfterExecutionResult> delegate;

    public BuildCacheStep(
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
        return Try.ofFailable(() -> work.isAllowedToLoadFromCache()
                ? tryLoadingFromCache(cacheKey, cacheableWork)
                : Optional.<BuildCacheLoadResult>empty()
            )
            .map(successfulLoad -> successfulLoad
                .map(cacheHit -> {
                    if (LOGGER.isInfoEnabled()) {
                        LOGGER.info("Loaded cache entry for {} with cache key {}",
                            work.getDisplayName(), cacheKey.getHashCode());
                    }
                    cleanLocalState(context.getWorkspace(), work);
                    OriginMetadata originMetadata = cacheHit.getOriginMetadata();
                    Try<Execution> execution = Try.successful(Execution.skipped(FROM_CACHE, work));
                    ExecutionOutputState afterExecutionOutputState = new DefaultExecutionOutputState(true, cacheHit.getResultingSnapshots(), originMetadata, true);
                    // Record snapshots of loaded result
                    afterExecutionOutputState.getOutputFilesProducedByWork().values().stream()
                        .flatMap(FileSystemSnapshot::roots)
                        .forEach(fileSystemAccess::record);
                    return new AfterExecutionResult(originMetadata.getExecutionTime(), execution, afterExecutionOutputState);
                })
                .orElseGet(() -> executeAndStoreInCache(cacheableWork, cacheKey, context))
            )
            .getOrMapFailure(loadFailure -> new AfterExecutionResult(
                Duration.ZERO,
                Try.failure(new RuntimeException(
                    String.format("Failed to load cache entry %s for %s: %s",
                        cacheKey.getHashCode(),
                        work.getDisplayName(),
                        loadFailure.getMessage()
                    ),
                    loadFailure
                )),
                null
            ));
    }

    private Optional<BuildCacheLoadResult> tryLoadingFromCache(BuildCacheKey cacheKey, CacheableWork cacheableWork) {
        if (cacheableWork.shouldInvalidateOutputsBeforeLoad()) {
            ImmutableList.Builder<String> roots = ImmutableList.builder();
            cacheableWork.visitOutputTrees((name, type, root) -> roots.add(root.getAbsolutePath()));
            fileSystemAccess.invalidate(roots.build());
        }
        return buildCache.load(cacheKey, cacheableWork);
    }

    private void cleanLocalState(File workspace, UnitOfWork work) {
        work.visitOutputs(workspace, new UnitOfWork.OutputVisitor() {
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

    private AfterExecutionResult executeAndStoreInCache(CacheableWork cacheableWork, BuildCacheKey cacheKey, C context) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Did not find cache entry for {} with cache key {}, executing instead",
                cacheableWork.getDisplayName(), cacheKey.getHashCode());
        }
        AfterExecutionResult result = executeWithoutCache(cacheableWork.work, context);
        try {
            result.getExecution().ifSuccessfulOrElse(
                executionResult -> storeInCacheUnlessDisabled(cacheableWork, cacheKey, result, executionResult),
                failure -> LOGGER.debug("Not storing result of {} in cache because the execution failed", cacheableWork.getDisplayName())
            );
            return result;
        } catch (Exception storeFailure) {
            return new AfterExecutionResult(Result.failed(storeFailure, result.getDuration()), result.getAfterExecutionOutputState().orElse(null));
        }
    }

    /**
     * Stores the results of the given work in the build cache, unless storing was disabled for this execution or work was untracked.
     * <p>
     * The former is currently used only for tasks and can be triggered via {@code org.gradle.api.internal.TaskOutputsEnterpriseInternal}.
     */
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
        private final UnitOfWork work;

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
            work.visitOutputs(workspace, new UnitOfWork.OutputVisitor() {
                @Override
                public void visitOutputProperty(String propertyName, TreeType type, UnitOfWork.OutputFileValueSupplier value) {
                    visitor.visitOutputTree(propertyName, type, value.getValue());
                }
            });
        }

        // TODO Make this much more explicit
        public boolean shouldInvalidateOutputsBeforeLoad() {
            // We don't need to invalidate outputs for immutable work as it's executed in a unique temporary workspace
            return work instanceof MutableUnitOfWork;
        }
    }
}
