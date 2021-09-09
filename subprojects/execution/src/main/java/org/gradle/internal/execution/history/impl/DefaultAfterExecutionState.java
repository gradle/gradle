/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.internal.execution.history.impl;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedMap;
import org.gradle.internal.execution.history.AfterExecutionState;
import org.gradle.internal.execution.history.BeforeExecutionState;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.snapshot.FileSystemSnapshot;
import org.gradle.internal.snapshot.ValueSnapshot;
import org.gradle.internal.snapshot.impl.ImplementationSnapshot;

public class DefaultAfterExecutionState implements AfterExecutionState {
    private final BeforeExecutionState beforeExecutionState;
    private final ImmutableSortedMap<String, FileSystemSnapshot> outputFileLocationSnapshots;

    public DefaultAfterExecutionState(
        BeforeExecutionState beforeExecutionState,
        ImmutableSortedMap<String, FileSystemSnapshot> outputFileLocationSnapshots
    ) {
        this.beforeExecutionState = beforeExecutionState;
        this.outputFileLocationSnapshots = outputFileLocationSnapshots;
    }

    @Override
    public ImmutableSortedMap<String, CurrentFileCollectionFingerprint> getInputFileProperties() {
        return beforeExecutionState.getInputFileProperties();
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
    public ImmutableSortedMap<String, FileSystemSnapshot> getOutputFilesProducedByWork() {
        return outputFileLocationSnapshots;
    }
}
