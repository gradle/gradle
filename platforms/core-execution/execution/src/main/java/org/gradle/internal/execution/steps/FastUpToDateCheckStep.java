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

import org.gradle.internal.execution.Execution;
import org.gradle.internal.execution.UnitOfWork;
import org.gradle.internal.execution.history.ExecutionOutputState;
import org.gradle.internal.execution.history.PreviousExecutionState;
import org.gradle.internal.execution.history.impl.DefaultExecutionOutputState;
import org.gradle.internal.fingerprint.FileCollectionFingerprint;
import org.gradle.internal.snapshot.FileSystemSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;

import static org.gradle.internal.execution.Execution.ExecutionOutcome.UP_TO_DATE;
import static org.gradle.internal.execution.steps.FastUpToDateCheckState.pathsOverlap;

/**
 * Skips the full up-to-date checking pipeline when VFS file watching confirms
 * that none of a task's input or output root paths have changed since the last build,
 * and no task that executed in this build has produced outputs overlapping with this task's inputs.
 *
 * <p>This step is designed to be zero-allocation on the fast path — it iterates directly
 * over existing immutable fingerprint and snapshot data without creating intermediate collections.
 * State sets (changedPaths, producedOutputRootPaths) are iterated once each in the outer loop.</p>
 */
public class FastUpToDateCheckStep<C extends PreviousExecutionContext, R extends CachingResult> implements Step<C, R> {

    private static final Logger LOGGER = LoggerFactory.getLogger(FastUpToDateCheckStep.class);

    private final FastUpToDateCheckState state;
    private final Step<? super C, ? extends R> delegate;

    public FastUpToDateCheckStep(FastUpToDateCheckState state, Step<? super C, ? extends R> delegate) {
        this.state = state;
        this.delegate = delegate;
    }

    @Override
    public R execute(UnitOfWork work, C context) {
        if (state.isFastPathEnabled() && !context.getNonIncrementalReason().isPresent()) {
            R fastResult = tryFastPath(work, context);
            if (fastResult != null) {
                return fastResult;
            }
        }

        R result = delegate.execute(work, context);
        recordProducedOutputsIfExecuted(result, context);
        return result;
    }

    private R tryFastPath(UnitOfWork work, C context) {
        PreviousExecutionState previousState = context.getPreviousExecutionState().orElse(null);
        if (previousState == null || !previousState.isSuccessful()) {
            return null;
        }

        if (hasAnyOverlap(previousState)) {
            return null;
        }

        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("Skipping {} as it is fast up-to-date.", work.getDisplayName());
        }

        ExecutionOutputState outputState = new DefaultExecutionOutputState(
            true,
            previousState.getOutputFilesProducedByWork(),
            previousState.getOriginMetadata(),
            true
        );
        @SuppressWarnings("unchecked")
        R shortcutResult = (R) CachingResult.shortcutResult(
            previousState.getOriginMetadata().getExecutionTime(),
            Execution.skipped(UP_TO_DATE, work),
            outputState,
            null,
            previousState.getOriginMetadata()
        );
        return shortcutResult;
    }

    /**
     * Checks for overlap between VFS changes / produced outputs and this task's input/output roots.
     * Iterates each state set (changedPaths, producedOutputRootPaths) at most once in the outer loop
     * to avoid repeated ConcurrentHashMap iterator creation.
     */
    private boolean hasAnyOverlap(PreviousExecutionState previousState) {
        // Check VFS changes against all input and output roots
        Set<String> changedPaths = state.getChangedPaths();
        if (!changedPaths.isEmpty()) {
            for (String changedPath : changedPaths) {
                if (overlapsWithAnyInputRoot(changedPath, previousState)) {
                    return true;
                }
                if (overlapsWithAnyOutputRoot(changedPath, previousState)) {
                    return true;
                }
            }
        }

        // Check produced output roots against input roots
        Set<String> producedOutputRootPaths = state.getProducedOutputRootPaths();
        if (!producedOutputRootPaths.isEmpty()) {
            for (String producedRoot : producedOutputRootPaths) {
                if (overlapsWithAnyInputRoot(producedRoot, previousState)) {
                    return true;
                }
            }
        }

        return false;
    }

    private static boolean overlapsWithAnyInputRoot(String path, PreviousExecutionState previousState) {
        for (FileCollectionFingerprint fingerprint : previousState.getInputFileProperties().values()) {
            for (String rootPath : fingerprint.getRootHashes().keySet()) {
                if (pathsOverlap(path, rootPath)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean overlapsWithAnyOutputRoot(String path, PreviousExecutionState previousState) {
        for (FileSystemSnapshot snapshot : previousState.getOutputFilesProducedByWork().values()) {
            // roots() returns a stream over an already-materialized list — lightweight
            if (snapshot.roots().anyMatch(root -> pathsOverlap(path, root.getAbsolutePath()))) {
                return true;
            }
        }
        return false;
    }

    private void recordProducedOutputsIfExecuted(R result, C context) {
        result.getExecution().ifSuccessfulOrElse(
            execution -> {
                Execution.ExecutionOutcome outcome = execution.getOutcome();
                if (outcome != UP_TO_DATE && outcome != Execution.ExecutionOutcome.SHORT_CIRCUITED) {
                    recordOutputRoots(result, context);
                }
            },
            failure -> recordOutputRoots(result, context)
        );
    }

    private void recordOutputRoots(R result, C context) {
        if (result.getAfterExecutionOutputState().isPresent()) {
            addOutputRootsFromSnapshots(result.getAfterExecutionOutputState().get().getOutputFilesProducedByWork());
        } else {
            context.getPreviousExecutionState().ifPresent(previousState ->
                addOutputRootsFromSnapshots(previousState.getOutputFilesProducedByWork())
            );
        }
    }

    private void addOutputRootsFromSnapshots(Map<String, FileSystemSnapshot> snapshots) {
        for (FileSystemSnapshot snapshot : snapshots.values()) {
            snapshot.roots().forEach(root -> state.recordProducedOutputRoot(root.getAbsolutePath()));
        }
    }
}
