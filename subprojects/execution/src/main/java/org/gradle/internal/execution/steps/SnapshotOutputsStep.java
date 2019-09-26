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
import org.gradle.caching.internal.origin.OriginMetadata;
import org.gradle.internal.Try;
import org.gradle.internal.execution.BeforeExecutionContext;
import org.gradle.internal.execution.CurrentSnapshotResult;
import org.gradle.internal.execution.ExecutionOutcome;
import org.gradle.internal.execution.Result;
import org.gradle.internal.execution.Step;
import org.gradle.internal.execution.UnitOfWork;
import org.gradle.internal.execution.history.AfterPreviousExecutionState;
import org.gradle.internal.execution.history.BeforeExecutionState;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.fingerprint.FileCollectionFingerprint;
import org.gradle.internal.id.UniqueId;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.BuildOperationType;
import org.gradle.internal.snapshot.FileSystemSnapshot;

public class SnapshotOutputsStep<C extends BeforeExecutionContext> extends BuildOperationStep<C, CurrentSnapshotResult> {
    private final UniqueId buildInvocationScopeId;
    private final Step<? super C, ? extends Result> delegate;

    public SnapshotOutputsStep(
        BuildOperationExecutor buildOperationExecutor,
        UniqueId buildInvocationScopeId,
        Step<? super C, ? extends Result> delegate
    ) {
        super(buildOperationExecutor);
        this.buildInvocationScopeId = buildInvocationScopeId;
        this.delegate = delegate;
    }

    @Override
    public CurrentSnapshotResult execute(C context) {
        Result result = delegate.execute(context);
        ImmutableSortedMap<String, CurrentFileCollectionFingerprint> finalOutputs = operation(
            operationContext -> {
                ImmutableSortedMap<String, CurrentFileCollectionFingerprint> outputSnapshots = captureOutputs(context);
                operationContext.setResult(Operation.Result.INSTANCE);
                return outputSnapshots;
            },
            BuildOperationDescriptor
                .displayName("Snapshot outputs after executing " + context.getWork().getDisplayName())
                .details(Operation.Details.INSTANCE)
        );

        OriginMetadata originMetadata = new OriginMetadata(buildInvocationScopeId.asString(), context.getWork().markExecutionTime());

        return new CurrentSnapshotResult() {
            @Override
            public ImmutableSortedMap<String, CurrentFileCollectionFingerprint> getFinalOutputs() {
                return finalOutputs;
            }

            @Override
            public OriginMetadata getOriginMetadata() {
                return originMetadata;
            }

            @Override
            public Try<ExecutionOutcome> getOutcome() {
                return result.getOutcome();
            }

            @Override
            public boolean isReused() {
                return false;
            }
        };
    }

    private static ImmutableSortedMap<String, CurrentFileCollectionFingerprint> captureOutputs(BeforeExecutionContext context) {
        UnitOfWork work = context.getWork();

        ImmutableSortedMap<String, FileCollectionFingerprint> afterPreviousExecutionStateOutputFingerprints = context.getAfterPreviousExecutionState()
            .map(AfterPreviousExecutionState::getOutputFileProperties)
            .orElse(ImmutableSortedMap.of());

        ImmutableSortedMap<String, FileSystemSnapshot> beforeExecutionOutputSnapshots = context.getBeforeExecutionState()
            .map(BeforeExecutionState::getOutputFileSnapshots)
            .orElse(ImmutableSortedMap.of());

        boolean hasDetectedOverlappingOutputs = context.getBeforeExecutionState()
            .flatMap(BeforeExecutionState::getDetectedOverlappingOutputs)
            .isPresent();

        ImmutableSortedMap<String, FileSystemSnapshot> afterExecutionOutputSnapshots = work.snapshotOutputsAfterExecution();

        return work.fingerprintAndFilterOutputSnapshots(
            afterPreviousExecutionStateOutputFingerprints,
            beforeExecutionOutputSnapshots,
            afterExecutionOutputSnapshots,
            hasDetectedOverlappingOutputs
        );
    }

    /*
     * This operation is only used here temporarily. Should be replaced with a more stable operation in the long term.
     */
    public interface Operation extends BuildOperationType<Operation.Details, Operation.Result> {
        interface Details {
            Details INSTANCE = new Details() {};
        }

        interface Result {
            Result INSTANCE = new Result() {};
        }
    }
}
