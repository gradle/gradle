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
import org.gradle.internal.snapshot.FileSystemSnapshot;

public class SnapshotOutputsStep<C extends BeforeExecutionContext> implements Step<C, CurrentSnapshotResult> {
    private final UniqueId buildInvocationScopeId;
    private final Step<? super C, ? extends Result> delegate;

    public SnapshotOutputsStep(
            UniqueId buildInvocationScopeId,
            Step<? super C, ? extends Result> delegate
    ) {
        this.buildInvocationScopeId = buildInvocationScopeId;
        this.delegate = delegate;
    }

    @Override
    public CurrentSnapshotResult execute(C context) {
        Result result = delegate.execute(context);

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
        ImmutableSortedMap<String, CurrentFileCollectionFingerprint> finalOutputs = work.fingerprintAndFilterOutputSnapshots(
            afterPreviousExecutionStateOutputFingerprints,
            beforeExecutionOutputSnapshots,
            afterExecutionOutputSnapshots,
            hasDetectedOverlappingOutputs);
        OriginMetadata originMetadata = new OriginMetadata(buildInvocationScopeId.asString(), work.markExecutionTime());
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
}
