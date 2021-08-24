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
import org.gradle.caching.internal.origin.OriginMetadata;
import org.gradle.internal.execution.history.PreviousExecutionState;
import org.gradle.internal.fingerprint.FileCollectionFingerprint;
import org.gradle.internal.snapshot.FileSystemSnapshot;
import org.gradle.internal.snapshot.ValueSnapshot;
import org.gradle.internal.snapshot.impl.ImplementationSnapshot;

public class DefaultPreviousExecutionState extends AbstractInputExecutionState<FileCollectionFingerprint> implements PreviousExecutionState {
    private final ImmutableSortedMap<String, FileSystemSnapshot> outputFilesProducedByWork;
    private final OriginMetadata originMetadata;
    private final boolean successful;

    public DefaultPreviousExecutionState(
        OriginMetadata originMetadata,
        ImplementationSnapshot implementation,
        ImmutableList<ImplementationSnapshot> additionalImplementations,
        ImmutableSortedMap<String, ValueSnapshot> inputProperties,
        ImmutableSortedMap<String, FileCollectionFingerprint> inputFileProperties,
        ImmutableSortedMap<String, FileSystemSnapshot> outputFilesProducedByWork,
        boolean successful
    ) {
        super(implementation, additionalImplementations, inputProperties, inputFileProperties);
        this.outputFilesProducedByWork = outputFilesProducedByWork;
        this.originMetadata = originMetadata;
        this.successful = successful;
    }

    @Override
    public ImmutableSortedMap<String, FileSystemSnapshot> getOutputFilesProducedByWork() {
        return outputFilesProducedByWork;
    }

    @Override
    public OriginMetadata getOriginMetadata() {
        return originMetadata;
    }

    @Override
    public boolean isSuccessful() {
        return successful;
    }
}
