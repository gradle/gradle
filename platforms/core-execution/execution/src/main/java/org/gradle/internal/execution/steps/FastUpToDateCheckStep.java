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
import org.gradle.internal.execution.history.PreviousExecutionState;
import org.gradle.internal.execution.history.impl.DefaultExecutionOutputState;
import org.gradle.internal.fingerprint.FileCollectionFingerprint;
import org.gradle.internal.snapshot.FileSystemLocationSnapshot;
import org.gradle.internal.snapshot.FileSystemSnapshot;
import org.gradle.internal.snapshot.FileSystemSnapshotHierarchyVisitor;
import org.gradle.internal.snapshot.SnapshotVisitResult;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

public class FastUpToDateCheckStep<C extends PreviousExecutionContext, R extends CachingResult> implements Step<C, R> {

    private final FastUpToDateCheckState state;
    private final Step<? super C, ? extends R> delegate;

    public FastUpToDateCheckStep(FastUpToDateCheckState state, Step<? super C, ? extends R> delegate) {
        this.state = state;
        this.delegate = delegate;
    }

    @Override
    public R execute(UnitOfWork work, C context) {
        if (state.isConfigurationCacheHit() && state.isWatchingFileSystem() && !state.isAnyTaskTouched()) {
            Optional<PreviousExecutionState> previousExecutionState = context.getPreviousExecutionState();
            if (previousExecutionState.isPresent()) {
                PreviousExecutionState previousState = previousExecutionState.get();
                if (!hasAnyInputOrOutputChanged(previousState, state.getChangedPaths())) {
                    @SuppressWarnings("unchecked")
                    R shortcutResult = (R) CachingResult.shortcutResult(
                        previousState.getOriginMetadata().getExecutionTime(),
                        Execution.skipped(Execution.ExecutionOutcome.UP_TO_DATE, work),
                        new DefaultExecutionOutputState(true, previousState.getOutputFilesProducedByWork(), previousState.getOriginMetadata(), true),
                        null,
                        previousState.getOriginMetadata()
                    );
                    return shortcutResult;
                }
            }
        }

        R result = delegate.execute(work, context);

        result.getExecution().ifSuccessfulOrElse(
            execution -> {
                Execution.ExecutionOutcome outcome = execution.getOutcome();
                if (outcome != Execution.ExecutionOutcome.UP_TO_DATE && outcome != Execution.ExecutionOutcome.SHORT_CIRCUITED) {
                    state.setAnyTaskTouched(true);
                }
            },
            failure -> state.setAnyTaskTouched(true)
        );

        return result;
    }

    private boolean hasAnyInputOrOutputChanged(PreviousExecutionState previousState, Set<Path> changedPaths) {
        if (changedPaths.isEmpty()) {
            return false;
        }

        for (Path changedPath : changedPaths) {
            Path current = changedPath;
            while (current != null) {
                String pathStr = current.toString();
                for (FileCollectionFingerprint fingerprint : previousState.getInputFileProperties().values()) {
                    if (fingerprint.getFingerprints().containsKey(pathStr)) {
                        return true;
                    }
                }
                current = current.getParent();
            }
        }

        Set<String> outputPaths = new HashSet<>();
        for (FileSystemSnapshot snapshot : previousState.getOutputFilesProducedByWork().values()) {
            snapshot.accept(new FileSystemSnapshotHierarchyVisitor() {
                @Override
                public SnapshotVisitResult visitEntry(FileSystemLocationSnapshot snapshot) {
                    outputPaths.add(snapshot.getAbsolutePath());
                    return SnapshotVisitResult.CONTINUE;
                }
            });
        }

        for (Path changedPath : changedPaths) {
            Path current = changedPath;
            while (current != null) {
                if (outputPaths.contains(current.toString())) {
                    return true;
                }
                current = current.getParent();
            }
        }

        return false;
    }
}
