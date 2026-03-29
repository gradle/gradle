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
import org.gradle.internal.Try;
import org.gradle.internal.execution.Execution;
import org.gradle.internal.execution.UnitOfWork;
import org.gradle.internal.execution.history.ExecutionOutputState;
import org.gradle.internal.execution.history.PreviousExecutionState;
import org.gradle.internal.execution.history.impl.DefaultExecutionOutputState;
import org.gradle.internal.fingerprint.FileCollectionFingerprint;
import org.gradle.internal.snapshot.FileSystemSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;

import static org.gradle.internal.execution.Execution.ExecutionOutcome.UP_TO_DATE;

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

        Set<String> inputRootPaths = extractInputRootPaths(previousState);
        Set<String> outputRootPaths = extractOutputRootPaths(previousState);

        Set<String> allRootPaths = new HashSet<>(inputRootPaths);
        allRootPaths.addAll(outputRootPaths);

        if (state.hasVfsChangesOverlappingWith(allRootPaths)) {
            return null;
        }
        if (state.hasProducedOutputsOverlappingWith(inputRootPaths)) {
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
        result.getAfterExecutionOutputState().ifPresent(outputState -> {
            Set<String> roots = extractOutputRootPathsFromSnapshots(outputState.getOutputFilesProducedByWork());
            state.recordProducedOutputRoots(roots);
        });
        if (!result.getAfterExecutionOutputState().isPresent()) {
            context.getPreviousExecutionState().ifPresent(previousState -> {
                Set<String> roots = extractOutputRootPaths(previousState);
                state.recordProducedOutputRoots(roots);
            });
        }
    }

    private static Set<String> extractInputRootPaths(PreviousExecutionState previousState) {
        Set<String> roots = new HashSet<>();
        for (FileCollectionFingerprint fingerprint : previousState.getInputFileProperties().values()) {
            roots.addAll(fingerprint.getRootHashes().keySet());
        }
        return roots;
    }

    private static Set<String> extractOutputRootPaths(PreviousExecutionState previousState) {
        return extractOutputRootPathsFromSnapshots(previousState.getOutputFilesProducedByWork());
    }

    private static Set<String> extractOutputRootPathsFromSnapshots(java.util.Map<String, FileSystemSnapshot> snapshots) {
        Set<String> roots = new HashSet<>();
        for (FileSystemSnapshot snapshot : snapshots.values()) {
            snapshot.roots().forEach(root -> roots.add(root.getAbsolutePath()));
        }
        return roots;
    }
}
