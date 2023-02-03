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

package org.gradle.internal.execution.history.impl;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedMap;
import org.gradle.internal.execution.history.BeforeExecutionState;
import org.gradle.internal.execution.history.OverlappingOutputs;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.snapshot.FileSystemSnapshot;
import org.gradle.internal.snapshot.ValueSnapshot;
import org.gradle.internal.snapshot.impl.ImplementationSnapshot;

import javax.annotation.Nullable;
import java.util.Optional;

public class DefaultBeforeExecutionState extends AbstractInputExecutionState<CurrentFileCollectionFingerprint> implements BeforeExecutionState {
    @Nullable
    private final OverlappingOutputs detectedOutputOverlaps;
    private final ImmutableSortedMap<String, FileSystemSnapshot> outputFileLocationSnapshots;

    public DefaultBeforeExecutionState(
        ImplementationSnapshot implementation,
        ImmutableList<ImplementationSnapshot> additionalImplementations,
        ImmutableSortedMap<String, ValueSnapshot> inputProperties,
        ImmutableSortedMap<String, CurrentFileCollectionFingerprint> inputFileProperties,
        ImmutableSortedMap<String, FileSystemSnapshot> outputFileLocationSnapshots,
        @Nullable OverlappingOutputs detectedOutputOverlaps
    ) {
        super(
            implementation,
            additionalImplementations,
            inputProperties,
            inputFileProperties
        );
        this.outputFileLocationSnapshots = outputFileLocationSnapshots;
        this.detectedOutputOverlaps = detectedOutputOverlaps;
    }

    @Override
    public ImmutableSortedMap<String, FileSystemSnapshot> getOutputFileLocationSnapshots() {
        return outputFileLocationSnapshots;
    }

    @Override
    public Optional<OverlappingOutputs> getDetectedOverlappingOutputs() {
        return Optional.ofNullable(detectedOutputOverlaps);
    }
}
