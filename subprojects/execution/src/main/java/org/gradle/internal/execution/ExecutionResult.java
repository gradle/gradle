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

package org.gradle.internal.execution;

import com.google.common.collect.ImmutableSortedMap;
import org.gradle.caching.internal.origin.OriginMetadata;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;

import javax.annotation.Nullable;

public abstract class ExecutionResult {
    public abstract ExecutionOutcome getOutcome();
    public abstract OriginMetadata getOriginMetadata();
    public abstract ImmutableSortedMap<String, CurrentFileCollectionFingerprint> getFinalOutputs();

    @Nullable
    public abstract Throwable getFailure();

    public static ExecutionResult success(ExecutionOutcome outcome, OriginMetadata originMetadata, ImmutableSortedMap<String, CurrentFileCollectionFingerprint> finalOutputs) {
        return new ExecutionResult() {
            @Override
            public ExecutionOutcome getOutcome() {
                return outcome;
            }

            @Override
            public OriginMetadata getOriginMetadata() {
                return originMetadata;
            }

            @Override
            public ImmutableSortedMap<String, CurrentFileCollectionFingerprint> getFinalOutputs() {
                return finalOutputs;
            }

            @Nullable
            @Override
            public Throwable getFailure() {
                return null;
            }
        };
    }

    public static ExecutionResult failure(Throwable failure, OriginMetadata originMetadata, ImmutableSortedMap<String, CurrentFileCollectionFingerprint> finalOutputs) {
        return new ExecutionResult() {
            @Override
            public ExecutionOutcome getOutcome() {
                return ExecutionOutcome.EXECUTED;
            }

            @Override
            public OriginMetadata getOriginMetadata() {
                return originMetadata;
            }

            @Override
            public ImmutableSortedMap<String, CurrentFileCollectionFingerprint> getFinalOutputs() {
                return finalOutputs;
            }

            @Override
            public Throwable getFailure() {
                return failure;
            }
        };
    }
}
