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
import org.gradle.api.file.FileCollection;
import org.gradle.caching.BuildCacheKey;
import org.gradle.caching.internal.CacheableEntity;
import org.gradle.caching.internal.controller.BuildCacheCommandFactory;
import org.gradle.caching.internal.controller.BuildCacheCommandFactory.LoadMetadata;
import org.gradle.caching.internal.controller.BuildCacheController;
import org.gradle.caching.internal.origin.OriginMetadata;
import org.gradle.internal.Try;
import org.gradle.internal.execution.ExecutionOutcome;
import org.gradle.internal.execution.ExecutionResult;
import org.gradle.internal.execution.OutputChangeListener;
import org.gradle.internal.execution.UnitOfWork;
import org.gradle.internal.execution.history.AfterExecutionState;
import org.gradle.internal.execution.history.BeforeExecutionState;
import org.gradle.internal.execution.history.impl.DefaultAfterExecutionState;
import org.gradle.internal.file.Deleter;
import org.gradle.internal.file.TreeType;
import org.gradle.internal.snapshot.FileSystemSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.Optional;

public class BuildCacheStep implements Step<IncrementalChangesContext, CurrentSnapshotResult> {
    private static final Logger LOGGER = LoggerFactory.getLogger(BuildCacheStep.class);

    private final BuildCacheController buildCache;
    private final BuildCacheCommandFactory commandFactory;
    private final Deleter deleter;
    private final OutputChangeListener outputChangeListener;
    private final Step<? super IncrementalChangesContext, ? extends CurrentSnapshotResult> delegate;

    public BuildCacheStep(
        BuildCacheController buildCache,
        BuildCacheCommandFactory commandFactory,
        Deleter deleter,
        OutputChangeListener outputChangeListener,
        Step<? super IncrementalChangesContext, ? extends CurrentSnapshotResult> delegate
    ) {
        this.buildCache = buildCache;
        this.commandFactory = commandFactory;
        this.deleter = deleter;
        this.outputChangeListener = outputChangeListener;
        this.delegate = delegate;
    }

    @Override
    public CurrentSnapshotResult execute(UnitOfWork work, IncrementalChangesContext context) {
        return context.getCachingState().fold(
            cachingEnabled -> executeWithCache(work, context, cachingEnabled.getKey(), cachingEnabled.getBeforeExecutionState()),
            cachingDisabled -> executeWithoutCache(work, context)
        );
    }

    private CurrentSnapshotResult executeWithCache(UnitOfWork work, IncrementalChangesContext context, BuildCacheKey cacheKey, BeforeExecutionState beforeExecutionState) {
        CacheableWork cacheableWork = new CacheableWork(context.getIdentity().getUniqueId(), context.getWorkspace(), work);
        return Try.ofFailable(() -> work.isAllowedToLoadFromCache()
                ? buildCache.load(commandFactory.createLoad(cacheKey, cacheableWork))
                : Optional.<LoadMetadata>empty()
            )
            .map(successfulLoad -> successfulLoad
                .map(cacheHit -> {
                    if (LOGGER.isInfoEnabled()) {
                        LOGGER.info("Loaded cache entry for {} with cache key {}",
                            work.getDisplayName(), cacheKey.getHashCode());
                    }
                    cleanLocalState(context.getWorkspace(), work);
                    OriginMetadata originMetadata = cacheHit.getOriginMetadata();
                    AfterExecutionState afterExecutionState = new DefaultAfterExecutionState(
                        beforeExecutionState,
                        cacheHit.getResultingSnapshots());
                    return (CurrentSnapshotResult) new CurrentSnapshotResult() {
                        @Override
                        public Try<ExecutionResult> getExecutionResult() {
                            return Try.successful(new ExecutionResult() {
                                @Override
                                public ExecutionOutcome getOutcome() {
                                    return ExecutionOutcome.FROM_CACHE;
                                }

                                @Override
                                public Object getOutput() {
                                    return work.loadRestoredOutput(context.getWorkspace());
                                }
                            });
                        }

                        @Override
                        public Duration getDuration() {
                            return originMetadata.getExecutionTime();
                        }

                        @Override
                        public OriginMetadata getOriginMetadata() {
                            return originMetadata;
                        }

                        @Override
                        public boolean isReused() {
                            return true;
                        }

                        @Override
                        public Optional<AfterExecutionState> getAfterExecutionState() {
                            return Optional.of(afterExecutionState);
                        }
                    };
                })
                .orElseGet(() -> executeAndStoreInCache(cacheableWork, cacheKey, context))
            )
            .getOrMapFailure(loadFailure -> {
                throw new RuntimeException(
                    String.format("Failed to load cache entry for %s",
                        work.getDisplayName()),
                    loadFailure
                );
            });
    }

    private void cleanLocalState(File workspace, UnitOfWork work) {
        work.visitOutputs(workspace, new UnitOfWork.OutputVisitor() {
            @Override
            public void visitLocalState(File localStateRoot) {
                try {
                    outputChangeListener.beforeOutputChange(ImmutableList.of(localStateRoot.getAbsolutePath()));
                    deleter.deleteRecursively(localStateRoot);
                } catch (IOException ex) {
                    throw new UncheckedIOException(String.format("Failed to clean up local state files for %s: %s", work.getDisplayName(), localStateRoot), ex);
                }
            }
        });
    }

    private CurrentSnapshotResult executeAndStoreInCache(CacheableWork cacheableWork, BuildCacheKey cacheKey, IncrementalChangesContext context) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Did not find cache entry for {} with cache key {}, executing instead",
                cacheableWork.getDisplayName(), cacheKey.getHashCode());
        }
        CurrentSnapshotResult result = executeWithoutCache(cacheableWork.work, context);
        result.getExecutionResult().ifSuccessfulOrElse(
            executionResult -> result.getAfterExecutionState()
                .ifPresent(afterExecutionState -> store(cacheableWork, cacheKey, afterExecutionState.getOutputFilesProducedByWork(), result.getOriginMetadata().getExecutionTime())),
            failure -> LOGGER.debug("Not storing result of {} in cache because the execution failed", cacheableWork.getDisplayName())
        );
        return result;
    }

    private void store(CacheableWork work, BuildCacheKey cacheKey, ImmutableSortedMap<String, FileSystemSnapshot> outputFilesProducedByWork, Duration executionTime) {
        try {
            buildCache.store(commandFactory.createStore(cacheKey, work, outputFilesProducedByWork, executionTime));
            if (LOGGER.isInfoEnabled()) {
                LOGGER.info("Stored cache entry for {} with cache key {}",
                    work.getDisplayName(), cacheKey.getHashCode());
            }
        } catch (Exception e) {
            throw new RuntimeException(
                String.format("Failed to store cache entry for %s",
                    work.getDisplayName()),
                e);
        }
    }

    private CurrentSnapshotResult executeWithoutCache(UnitOfWork work, IncrementalChangesContext context) {
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
                public void visitOutputProperty(String propertyName, TreeType type, File root, FileCollection contents) {
                    visitor.visitOutputTree(propertyName, type, root);
                }
            });
        }
    }
}
