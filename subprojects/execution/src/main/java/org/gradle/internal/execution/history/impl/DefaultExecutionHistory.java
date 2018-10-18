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
import org.gradle.internal.execution.history.ExecutionHistory;
import org.gradle.internal.fingerprint.FileCollectionFingerprint;
import org.gradle.internal.snapshot.ValueSnapshot;
import org.gradle.internal.snapshot.impl.ImplementationSnapshot;

import java.util.List;
import java.util.SortedMap;

public class DefaultExecutionHistory implements ExecutionHistory {
    private final OriginMetadata originMetadata;
    private final ImplementationSnapshot implementation;
    private final ImmutableList<ImplementationSnapshot> additionalImplementations;
    private final ImmutableSortedMap<String, ValueSnapshot> inputProperties;
    private final ImmutableSortedMap<String, FileCollectionFingerprint> inputFileProperties;
    private final ImmutableSortedMap<String, FileCollectionFingerprint> outputFileProperties;
    private final boolean successful;

    public DefaultExecutionHistory(
        OriginMetadata originMetadata,
        ImplementationSnapshot implementation,
        List<ImplementationSnapshot> additionalImplementations,
        SortedMap<String, ? extends ValueSnapshot> inputProperties,
        SortedMap<String, ? extends FileCollectionFingerprint> inputFileProperties,
        SortedMap<String, ? extends FileCollectionFingerprint> outputFileProperties,
        boolean successful
    ) {
        this.originMetadata = originMetadata;
        this.implementation = implementation;
        this.additionalImplementations = ImmutableList.copyOf(additionalImplementations);
        this.inputProperties = ImmutableSortedMap.copyOfSorted(inputProperties);
        this.inputFileProperties = ImmutableSortedMap.copyOfSorted(inputFileProperties);
        this.outputFileProperties = ImmutableSortedMap.copyOfSorted(outputFileProperties);
        this.successful = successful;
    }

    @Override
    public OriginMetadata getOriginMetadata() {
        return originMetadata;
    }

    @Override
    public ImplementationSnapshot getImplementation() {
        return implementation;
    }

    @Override
    public ImmutableList<ImplementationSnapshot> getAdditionalImplementations() {
        return additionalImplementations;
    }

    @Override
    public ImmutableSortedMap<String, ValueSnapshot> getInputProperties() {
        return inputProperties;
    }

    @Override
    public ImmutableSortedMap<String, FileCollectionFingerprint> getInputFileProperties() {
        return inputFileProperties;
    }

    @Override
    public ImmutableSortedMap<String, FileCollectionFingerprint> getOutputFileProperties() {
        return outputFileProperties;
    }

    @Override
    public boolean isSuccessful() {
        return successful;
    }
}
