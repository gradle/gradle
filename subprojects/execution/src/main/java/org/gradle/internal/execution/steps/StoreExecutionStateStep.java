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
import org.gradle.internal.execution.UnitOfWork;
import org.gradle.internal.execution.history.AfterExecutionState;
import org.gradle.internal.execution.history.ExecutionHistoryStore;
import org.gradle.internal.execution.history.changes.ChangeDetectorVisitor;
import org.gradle.internal.execution.history.changes.OutputFileChanges;
import org.gradle.internal.snapshot.FileSystemSnapshot;

import java.util.Optional;

public class StoreExecutionStateStep<C extends BeforeExecutionContext> implements Step<C, CurrentSnapshotResult> {
    private final Step<? super C, ? extends CurrentSnapshotResult> delegate;

    public StoreExecutionStateStep(
        Step<? super C, ? extends CurrentSnapshotResult> delegate
    ) {
        this.delegate = delegate;
    }

    @Override
    public CurrentSnapshotResult execute(UnitOfWork work, C context) {
        CurrentSnapshotResult result = delegate.execute(work, context);
        UnitOfWork.Identity identity = context.getIdentity();
        context.getHistory()
            .ifPresent(history -> storeState(context, history, identity.getUniqueId(), result));
        return result;
    }

    private void storeState(C context, ExecutionHistoryStore executionHistoryStore, String uniqueId, CurrentSnapshotResult result) {
        ImmutableSortedMap<String, FileSystemSnapshot> outputFilesProducedByWork = result.getOutputFilesProduceByWork();
        context.getBeforeExecutionState().ifPresent(beforeExecutionState -> {
            boolean successful = result.getExecutionResult().isSuccessful();
            executionHistoryStore.store(
                successful,
                uniqueId,
                result.getOriginMetadata(),
                result.getCacheKey(),
                beforeExecutionState.getImplementation(),
                beforeExecutionState.getAdditionalImplementations(),
                beforeExecutionState.getInputProperties(),
                beforeExecutionState.getInputFileProperties(),
                outputFilesProducedByWork
            );
        });
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private static boolean didChangeOutput(Optional<AfterExecutionState> afterPreviousExecutionState, ImmutableSortedMap<String, FileSystemSnapshot> current) {
        // If there is no previous state, then we do have output changes
        if (!afterPreviousExecutionState.isPresent()) {
            return true;
        }

        // If there are different output properties compared to the previous execution, then we do have output changes
        ImmutableSortedMap<String, FileSystemSnapshot> previous = afterPreviousExecutionState.get().getOutputFilesProducedByWork();
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
