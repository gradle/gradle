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
import org.gradle.internal.execution.BeforeExecutionContext;
import org.gradle.internal.execution.CurrentSnapshotResult;
import org.gradle.internal.execution.Step;
import org.gradle.internal.execution.UnitOfWork;
import org.gradle.internal.execution.history.AfterPreviousExecutionState;
import org.gradle.internal.execution.history.ExecutionHistoryStore;
import org.gradle.internal.execution.history.changes.ChangeDetectorVisitor;
import org.gradle.internal.execution.history.changes.OutputFileChanges;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.fingerprint.FileCollectionFingerprint;

import java.util.Optional;

public class StoreExecutionStateStep<C extends BeforeExecutionContext> implements Step<C, CurrentSnapshotResult> {
    private final Step<? super C, ? extends CurrentSnapshotResult> delegate;

    public StoreExecutionStateStep(
        Step<? super C, ? extends CurrentSnapshotResult> delegate
    ) {
        this.delegate = delegate;
    }

    @Override
    public CurrentSnapshotResult execute(C context) {
        CurrentSnapshotResult result = delegate.execute(context);
        context.getWork().getExecutionHistoryStore()
            .ifPresent(executionHistoryStore -> storeState(context, executionHistoryStore, result));
        return result;
    }

    private void storeState(C context, ExecutionHistoryStore executionHistoryStore, CurrentSnapshotResult result) {
        ImmutableSortedMap<String, CurrentFileCollectionFingerprint> finalOutputs = result.getFinalOutputs();
        context.getBeforeExecutionState().ifPresent(beforeExecutionState -> {
            boolean successful = result.getOutcome().isSuccessful();
            // We do not store the history if there was a failure and the outputs did not change, since then the next execution can be incremental.
            // For example the current execution fails because of a compile failure and for the next execution the source file is fixed, so only the one changed source file needs to be compiled.
            if (successful
                || didChangeOutput(context.getAfterPreviousExecutionState(), finalOutputs)) {
                UnitOfWork work = context.getWork();
                executionHistoryStore.store(
                    work.getIdentity(),
                    result.getOriginMetadata(),
                    beforeExecutionState.getImplementation(),
                    beforeExecutionState.getAdditionalImplementations(),
                    beforeExecutionState.getInputProperties(),
                    beforeExecutionState.getInputFileProperties(),
                    finalOutputs,
                    successful
                );
            }
        });
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private static boolean didChangeOutput(Optional<AfterPreviousExecutionState> afterPreviousExecutionState, ImmutableSortedMap<String, CurrentFileCollectionFingerprint> current) {
        // If there is no previous state, then we do have output changes
        if (!afterPreviousExecutionState.isPresent()) {
            return true;
        }

        // If there are different output properties compared to the previous execution, then we do have output changes
        ImmutableSortedMap<String, FileCollectionFingerprint> previous = afterPreviousExecutionState.get().getOutputFileProperties();
        if (!previous.keySet().equals(current.keySet())) {
            return true;
        }

        // Otherwise do deep compare of outputs
        ChangeDetectorVisitor visitor = new ChangeDetectorVisitor();
        OutputFileChanges changes = new OutputFileChanges(previous, current);
        changes.accept(visitor);
        return visitor.hasAnyChanges();
    }
}
