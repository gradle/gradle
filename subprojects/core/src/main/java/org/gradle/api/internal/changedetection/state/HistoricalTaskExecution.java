/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.internal.changedetection.state;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedMap;
import org.gradle.api.NonNullApi;
import org.gradle.caching.internal.origin.OriginMetadata;
import org.gradle.internal.execution.history.AfterPreviousExecutionState;
import org.gradle.internal.fingerprint.FileCollectionFingerprint;
import org.gradle.internal.snapshot.ValueSnapshot;
import org.gradle.internal.snapshot.impl.ImplementationSnapshot;

/**
 * State of a task when it was executed.
 */
@NonNullApi
public class HistoricalTaskExecution extends AbstractTaskExecution implements AfterPreviousExecutionState {

    private final boolean successful;
    private final OriginMetadata originMetadata;
    private final ImmutableSortedMap<String, FileCollectionFingerprint> inputFingerprints;
    private final ImmutableSortedMap<String, FileCollectionFingerprint> outputFingerprints;

    public HistoricalTaskExecution(
        ImplementationSnapshot taskImplementation,
        ImmutableList<ImplementationSnapshot> taskActionsImplementations,
        ImmutableSortedMap<String, ValueSnapshot> inputProperties,
        ImmutableSortedMap<String, FileCollectionFingerprint> inputFingerprints,
        ImmutableSortedMap<String, FileCollectionFingerprint> outputFingerprints,
        boolean successful,
        OriginMetadata originMetadata
    ) {
        super(taskImplementation, taskActionsImplementations, inputProperties);
        this.inputFingerprints = inputFingerprints;
        this.outputFingerprints = outputFingerprints;
        this.successful = successful;
        this.originMetadata = originMetadata;
    }

    @Override
    public boolean isSuccessful() {
        return successful;
    }

    @Override
    public OriginMetadata getOriginMetadata() {
        return originMetadata;
    }

    @Override
    public ImmutableSortedMap<String, FileCollectionFingerprint> getInputFileProperties() {
        return inputFingerprints;
    }

    @Override
    public ImmutableSortedMap<String, FileCollectionFingerprint> getOutputFileProperties() {
        return outputFingerprints;
    }
}
