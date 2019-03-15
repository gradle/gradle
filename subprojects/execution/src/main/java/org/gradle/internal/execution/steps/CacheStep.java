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

import com.google.common.collect.ImmutableSortedMap;
import org.apache.commons.io.FileUtils;
import org.gradle.api.UncheckedIOException;
import org.gradle.caching.BuildCacheKey;
import org.gradle.caching.internal.command.BuildCacheCommandFactory;
import org.gradle.caching.internal.controller.BuildCacheController;
import org.gradle.caching.internal.origin.OriginMetadata;
import org.gradle.internal.Try;
import org.gradle.internal.execution.CacheHandler;
import org.gradle.internal.execution.CurrentSnapshotResult;
import org.gradle.internal.execution.ExecutionOutcome;
import org.gradle.internal.execution.IncrementalChangesContext;
import org.gradle.internal.execution.Step;
import org.gradle.internal.execution.UnitOfWork;
import org.gradle.internal.execution.history.AfterPreviousExecutionState;
import org.gradle.internal.execution.history.BeforeExecutionState;
import org.gradle.internal.execution.history.changes.ExecutionStateChanges;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Optional;

public class CacheStep implements Step<IncrementalChangesContext, CurrentSnapshotResult> {
    private static final Logger LOGGER = LoggerFactory.getLogger(CacheStep.class);

    private final BuildCacheController buildCache;
    private final BuildCacheCommandFactory commandFactory;
    private final Step<? super IncrementalChangesContext, ? extends CurrentSnapshotResult> delegate;

    public CacheStep(
            BuildCacheController buildCache,
            BuildCacheCommandFactory commandFactory,
            Step<? super IncrementalChangesContext, ? extends CurrentSnapshotResult> delegate
    ) {
        this.buildCache = buildCache;
        this.commandFactory = commandFactory;
        this.delegate = delegate;
    }

    @Override
    public CurrentSnapshotResult execute(IncrementalChangesContext context) {
        if (!buildCache.isEnabled()) {
            return executeWithoutCache(context);
        }
        UnitOfWork work = context.getWork();
        CacheHandler cacheHandler = work.createCacheHandler();
        return Try.ofFailable(() -> cacheHandler.load(cacheKey -> buildCache.load(commandFactory.createLoad(cacheKey, work))))
            .map(successfulLoad -> successfulLoad
                .map(cacheHit -> {
                    OriginMetadata originMetadata = cacheHit.getOriginMetadata();
                    ImmutableSortedMap<String, CurrentFileCollectionFingerprint> finalOutputs = cacheHit.getResultingSnapshots();
                    return (CurrentSnapshotResult) new CurrentSnapshotResult() {
                        @Override
                        public Try<ExecutionOutcome> getOutcome() {
                            return Try.successful(ExecutionOutcome.FROM_CACHE);
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
                        public ImmutableSortedMap<String, CurrentFileCollectionFingerprint> getFinalOutputs() {
                            return finalOutputs;
                        }
                    };
                })
                .orElseGet(() -> executeAndStoreInCache(cacheHandler, context))
            )
            .orElseMapFailure(loadFailure -> {
                LOGGER.warn("Failed to load cache entry for {}, cleaning outputs and falling back to (non-incremental) execution",
                    work.getDisplayName(), loadFailure);
                cleanupTreesAfterLoadFailure(work);

                return executeAndStoreInCache(cacheHandler, new IncrementalChangesContext() {
                    @Override
                    public Optional<ExecutionStateChanges> getChanges() {
                        // Clear change information to avoid incremental execution after failed load
                        return Optional.empty();
                    }

                    @Override
                    public Optional<String> getRebuildReason() {
                        return context.getRebuildReason();
                    }

                    @Override
                    public Optional<AfterPreviousExecutionState> getAfterPreviousExecutionState() {
                        return context.getAfterPreviousExecutionState();
                    }

                    @Override
                    public Optional<BeforeExecutionState> getBeforeExecutionState() {
                        return context.getBeforeExecutionState();
                    }

                    @Override
                    public UnitOfWork getWork() {
                        return work;
                    }
                });
            });
    }

    private static void cleanupTreesAfterLoadFailure(UnitOfWork work) {
        work.visitOutputTrees((name, type, root) -> {
            try {
                if (root.exists()) {
                    if (root.isDirectory()) {
                        FileUtils.cleanDirectory(root);
                    } else {
                        FileUtils.forceDelete(root);
                    }
                }
            } catch (IOException ex) {
                throw new UncheckedIOException(String.format("Failed to clean up files for tree '%s' of %s: %s", name, work.getDisplayName(), root), ex);
            }
        });
    }

    private CurrentSnapshotResult executeAndStoreInCache(CacheHandler cacheHandler, IncrementalChangesContext context) {
        CurrentSnapshotResult executionResult = executeWithoutCache(context);
        executionResult.getOutcome().ifSuccessfulOrElse(
            outcome -> cacheHandler.store(cacheKey -> store(context.getWork(), cacheKey, executionResult)),
            failure -> LOGGER.debug("Not storing result of {} in cache because the execution failed", context.getWork().getDisplayName())
        );
        return executionResult;
    }

    private void store(UnitOfWork work, BuildCacheKey cacheKey, CurrentSnapshotResult result) {
        try {
            // TODO This could send in the whole origin metadata
            buildCache.store(commandFactory.createStore(cacheKey, work, result.getFinalOutputs(), result.getOriginMetadata().getExecutionTime()));
        } catch (Exception e) {
            LOGGER.warn("Failed to store cache entry {}", cacheKey.getDisplayName(), e);
        }
    }

    private CurrentSnapshotResult executeWithoutCache(IncrementalChangesContext context) {
        return delegate.execute(context);
    }
}
