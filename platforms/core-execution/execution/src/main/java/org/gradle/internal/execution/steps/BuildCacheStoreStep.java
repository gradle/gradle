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

import com.google.common.collect.ImmutableSortedMap;
import org.gradle.caching.BuildCacheKey;
import org.gradle.caching.internal.CacheableEntity;
import org.gradle.caching.internal.controller.BuildCacheController;
import org.gradle.internal.execution.ExecutionEngine;
import org.gradle.internal.execution.UnitOfWork;
import org.gradle.internal.file.TreeType;
import org.gradle.internal.snapshot.FileSystemSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.time.Duration;

public class BuildCacheStoreStep<C extends IdentityContext> implements Step<C, WorkspaceResult> {
    private static final Logger LOGGER = LoggerFactory.getLogger(BuildCacheStoreStep.class);

    private final BuildCacheController buildCache;
    private final Step<? super IdentityContext, ? extends WorkspaceResult> delegate;

    public BuildCacheStoreStep(BuildCacheController buildCache, Step<? super IdentityContext, ? extends WorkspaceResult> delegate) {
        this.buildCache = buildCache;
        this.delegate = delegate;
    }

    @Override
    public WorkspaceResult execute(UnitOfWork work, C context) {
        WorkspaceResult result = delegate.execute(work, context);
        if (result.getExecution().isSuccessful()
            && result.getExecution().get().getOutcome() != ExecutionEngine.ExecutionOutcome.SHORT_CIRCUITED
            && result.getExecution().get().getOutcome() != ExecutionEngine.ExecutionOutcome.UP_TO_DATE
            && result.getExecution().get().canStoreOutputsInCache()
            && result.getCachingState().whenEnabled().isPresent()
            && result.getCachingState().getCacheKeyCalculatedState().isPresent()) {
            CacheableWork cacheableWork = new CacheableWork(context.getIdentity().getUniqueId(), result.getWorkspace(), work);
            result.getCachingState().getCacheKeyCalculatedState().ifPresent(cacheKeyCalculatedState -> {
                result.getExecution().ifSuccessfulOrElse(
                    executionResult -> storeInCacheUnlessDisabled(cacheableWork, cacheKeyCalculatedState.getKey(), result, executionResult),
                    failure -> LOGGER.debug("Not storing result of {} in cache because the execution failed", cacheableWork.getDisplayName())
                );

            });
        }
        return result;
    }

    private void storeInCacheUnlessDisabled(CacheableWork cacheableWork, BuildCacheKey cacheKey, WorkspaceResult result, ExecutionEngine.Execution executionResult) {
        if (executionResult.canStoreOutputsInCache()) {
            result.getAfterExecutionOutputState()
                .ifPresent(afterExecutionState -> store(cacheableWork, cacheKey, result.getWorkspaceSnapshots(), afterExecutionState.getOriginMetadata().getExecutionTime()));
        } else {
            LOGGER.debug("Not storing result of {} in cache because storing was disabled for this execution", cacheableWork.getDisplayName());
        }
    }

    private void store(CacheableWork work, BuildCacheKey cacheKey, ImmutableSortedMap<String, FileSystemSnapshot> outputFilesProducedByWork, Duration executionTime) {
        try {
            buildCache.storeAsync(cacheKey, work, outputFilesProducedByWork, executionTime);
        } catch (Exception e) {
            LOGGER.warn("Failed to store result of {} in cache", work.getDisplayName(), e);
        }
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
    }
}
