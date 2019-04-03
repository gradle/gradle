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
import org.gradle.caching.internal.command.BuildCacheCommandFactory.LoadMetadata;
import org.gradle.caching.internal.controller.BuildCacheController;
import org.gradle.caching.internal.origin.OriginMetadata;
import org.gradle.internal.Try;
import org.gradle.internal.execution.CurrentSnapshotResult;
import org.gradle.internal.execution.ExecutionOutcome;
import org.gradle.internal.execution.IncrementalChangesContext;
import org.gradle.internal.execution.Step;
import org.gradle.internal.execution.UnitOfWork;
import org.gradle.internal.execution.caching.CachingState;
import org.gradle.internal.execution.history.AfterPreviousExecutionState;
import org.gradle.internal.execution.history.BeforeExecutionState;
import org.gradle.internal.execution.history.changes.ExecutionStateChanges;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Optional;

public class CacheStep implements Step<IncrementalChangesContext, CurrentSnapshotResult> {
    private static final Logger LOGGER = LoggerFactory.getLogger(CacheStep.class);

    private static final String FAILED_LOAD_REBUILD_REASON = "Outputs removed due to failed load from cache";

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
        CachingState cachingState = context.getCachingState();
        //noinspection OptionalGetWithoutIsPresent
        return cachingState.getDisabledReasons().isEmpty()
            ? executeWithCache(context, cachingState.getKey().get())
            : executeWithoutCache(context);
    }

    private CurrentSnapshotResult executeWithCache(IncrementalChangesContext context, BuildCacheKey cacheKey) {
        UnitOfWork work = context.getWork();
        return Try.ofFailable(() -> work.isAllowedToLoadFromCache()
                ? buildCache.load(commandFactory.createLoad(cacheKey, work))
                : Optional.<LoadMetadata>empty()
            )
            .map(successfulLoad -> successfulLoad
                .map(cacheHit -> {
                    cleanLocalState(work);
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
                .orElseGet(() -> executeAndStoreInCache(cacheKey, context))
            )
            .orElseMapFailure(loadFailure -> {
                LOGGER.warn("Failed to load cache entry for {}, cleaning outputs and falling back to (non-incremental) execution",
                    work.getDisplayName(), loadFailure);

                cleanLocalState(work);
                cleanOutputsAfterLoadFailure(work);
                Optional<ExecutionStateChanges> rebuildChanges = context.getChanges().map(changes -> changes.withEnforcedRebuild(FAILED_LOAD_REBUILD_REASON));

                return executeAndStoreInCache(cacheKey, new IncrementalChangesContext() {
                    @Override
                    public Optional<ExecutionStateChanges> getChanges() {
                        return rebuildChanges;
                    }

                    @Override
                    public CachingState getCachingState() {
                        return context.getCachingState();
                    }

                    @Override
                    public Optional<String> getRebuildReason() {
                        return Optional.of(FAILED_LOAD_REBUILD_REASON);
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

    private static void cleanLocalState(UnitOfWork work) {
        work.visitLocalState(localStateFile -> {
            try {
                remove(localStateFile);
            } catch (IOException ex) {
                throw new UncheckedIOException(String.format("Failed to clean up local state files for %s: %s", work.getDisplayName(), localStateFile), ex);
            }
        });
    }

    private static void cleanOutputsAfterLoadFailure(UnitOfWork work) {
        work.visitOutputProperties((name, type, roots) -> {
            for (File root : roots) {
                try {
                    remove(root);
                } catch (IOException ex) {
                    throw new UncheckedIOException(String.format("Failed to clean up files for tree '%s' of %s: %s", name, work.getDisplayName(), root), ex);
                }
            }
        });
    }

    private static void remove(File root) throws IOException {
        if (root.exists()) {
            if (root.isDirectory()) {
                FileUtils.cleanDirectory(root);
            } else {
                FileUtils.forceDelete(root);
            }
        }
    }

    private CurrentSnapshotResult executeAndStoreInCache(BuildCacheKey cacheKey, IncrementalChangesContext context) {
        CurrentSnapshotResult executionResult = executeWithoutCache(context);
        executionResult.getOutcome().ifSuccessfulOrElse(
            outcome -> store(context.getWork(), cacheKey, executionResult),
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
