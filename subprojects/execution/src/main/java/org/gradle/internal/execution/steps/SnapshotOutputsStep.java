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
import org.gradle.internal.execution.Context;
import org.gradle.internal.execution.CurrentSnapshotResult;
import org.gradle.internal.execution.ExecutionOutcome;
import org.gradle.internal.execution.Result;
import org.gradle.internal.execution.Step;
import org.gradle.internal.execution.UnitOfWork;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.id.UniqueId;

public class SnapshotOutputsStep<C extends Context> implements Step<C, CurrentSnapshotResult> {
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
        ImmutableSortedMap<String, CurrentFileCollectionFingerprint> finalOutputs = work.snapshotAfterOutputsGenerated();
        OriginMetadata originMetadata = new OriginMetadata(buildInvocationScopeId, work.markExecutionTime());
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
