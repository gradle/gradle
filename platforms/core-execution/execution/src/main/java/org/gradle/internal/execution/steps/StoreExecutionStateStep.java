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
import org.gradle.caching.internal.origin.OriginMetadata;
import org.gradle.internal.execution.UnitOfWork;
import org.gradle.internal.execution.history.AfterExecutionState;
import org.gradle.internal.execution.history.BeforeExecutionState;
import org.gradle.internal.execution.history.ExecutionOutputState;
import org.gradle.internal.execution.history.changes.ChangeDetectorVisitor;
import org.gradle.internal.execution.history.changes.OutputFileChanges;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.snapshot.FileSystemSnapshot;
import org.gradle.internal.snapshot.ValueSnapshot;
import org.gradle.internal.snapshot.impl.ImplementationSnapshot;

public class StoreExecutionStateStep<C extends BeforeExecutionContext, R extends AfterExecutionResult> implements Step<C, R> {
    private final Step<? super C, ? extends R> delegate;

    public StoreExecutionStateStep(
        Step<? super C, ? extends R> delegate
    ) {
        this.delegate = delegate;
    }

    @Override
    public R execute(UnitOfWork work, C context) {
        R result = delegate.execute(work, context);
        context.getHistory()
            .ifPresent(history -> context.getBeforeExecutionState()
                .flatMap(beforeExecutionState -> result.getAfterExecutionOutputState()
                    .filter(afterExecutionState -> result.getExecution().isSuccessful() || shouldPreserveFailedState(context, afterExecutionState))
                    .map(executionOutputState -> new DefaultAfterExecutionState(beforeExecutionState, executionOutputState)))
                .ifPresent(afterExecutionState -> history.store(context.getIdentity().getUniqueId(), afterExecutionState)));
        return result;
    }

    private static <C extends BeforeExecutionContext, R extends AfterExecutionResult> boolean shouldPreserveFailedState(C context, ExecutionOutputState afterExecutionOutputState) {
        // We do not store the history if there was a failure and the outputs did not change, since then the next execution can be incremental.
        // For example the current execution fails because of a compilation failure and for the next execution the source file is fixed,
        // so only the one changed source file needs to be compiled.
        // If there is no previous state, then we do have output changes
        return context.getPreviousExecutionState()
            .map(previewExecutionState -> didOutputsChange(
                previewExecutionState.getOutputFilesProducedByWork(),
                afterExecutionOutputState.getOutputFilesProducedByWork()))
            .orElse(true);
    }

    private static boolean didOutputsChange(ImmutableSortedMap<String, FileSystemSnapshot> previous, ImmutableSortedMap<String, FileSystemSnapshot> current) {
        // If there are different output properties compared to the previous execution, then we do have output changes
        if (!previous.keySet().equals(current.keySet())) {
            return true;
        }

        // Otherwise, do deep compare of outputs
        ChangeDetectorVisitor visitor = new ChangeDetectorVisitor();
        OutputFileChanges changes = new OutputFileChanges(previous, current);
        changes.accept(visitor);
        return visitor.hasAnyChanges();
    }

    private static class DefaultAfterExecutionState implements AfterExecutionState {
        private final BeforeExecutionState beforeExecutionState;
        private final ExecutionOutputState afterExecutionOutputState;

        public DefaultAfterExecutionState(BeforeExecutionState beforeExecutionState, ExecutionOutputState afterExecutionOutputState) {
            this.beforeExecutionState = beforeExecutionState;
            this.afterExecutionOutputState = afterExecutionOutputState;
        }

        @Override
        public ImplementationSnapshot getImplementation() {
            return beforeExecutionState.getImplementation();
        }

        @Override
        public ImmutableList<ImplementationSnapshot> getAdditionalImplementations() {
            return beforeExecutionState.getAdditionalImplementations();
        }

        @Override
        public ImmutableSortedMap<String, ValueSnapshot> getInputProperties() {
            return beforeExecutionState.getInputProperties();
        }

        @Override
        public ImmutableSortedMap<String, CurrentFileCollectionFingerprint> getInputFileProperties() {
            return beforeExecutionState.getInputFileProperties();
        }

        @Override
        public boolean isSuccessful() {
            return afterExecutionOutputState.isSuccessful();
        }

        @Override
        public ImmutableSortedMap<String, FileSystemSnapshot> getOutputFilesProducedByWork() {
            return afterExecutionOutputState.getOutputFilesProducedByWork();
        }

        @Override
        public OriginMetadata getOriginMetadata() {
            return afterExecutionOutputState.getOriginMetadata();
        }

        @Override
        public boolean isReused() {
            return afterExecutionOutputState.isReused();
        }
    }
}
