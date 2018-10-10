/*
 * Copyright 2017 the original author or authors.
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

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Maps;
import org.gradle.api.NonNullApi;
import org.gradle.api.internal.OverlappingOutputs;
import org.gradle.api.internal.tasks.OriginTaskExecutionMetadata;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.fingerprint.FileCollectionFingerprint;
import org.gradle.internal.fingerprint.HistoricalFileCollectionFingerprint;
import org.gradle.internal.snapshot.ValueSnapshot;
import org.gradle.internal.snapshot.impl.ImplementationSnapshot;

import javax.annotation.Nullable;

@NonNullApi
public class CurrentTaskExecution extends AbstractTaskExecution {

    private static final Function<FileCollectionFingerprint, HistoricalFileCollectionFingerprint> ARCHIVE_FINGERPRINT = new Function<FileCollectionFingerprint, HistoricalFileCollectionFingerprint>() {
        @Override
        @SuppressWarnings("NullableProblems")
        public HistoricalFileCollectionFingerprint apply(FileCollectionFingerprint value) {
            return value.archive();
        }
    };

    private ImmutableSortedMap<String, CurrentFileCollectionFingerprint> outputFingerprints;
    private final ImmutableSortedMap<String, CurrentFileCollectionFingerprint> inputFingerprints;
    private final OverlappingOutputs detectedOverlappingOutputs;
    private Boolean successful;
    private OriginTaskExecutionMetadata originExecutionMetadata;

    public CurrentTaskExecution(
        ImplementationSnapshot taskImplementation,
        ImmutableList<ImplementationSnapshot> taskActionImplementations,
        ImmutableSortedMap<String, ValueSnapshot> inputProperties,
        ImmutableSortedSet<String> outputPropertyNames,
        ImmutableSortedMap<String, CurrentFileCollectionFingerprint> inputFingerprints,
        ImmutableSortedMap<String, CurrentFileCollectionFingerprint> outputFingerprintsBeforeExecution,
        @Nullable OverlappingOutputs detectedOverlappingOutputs
    ) {
        super(taskImplementation, taskActionImplementations, inputProperties, outputPropertyNames);
        this.outputFingerprints = outputFingerprintsBeforeExecution;
        this.inputFingerprints = inputFingerprints;
        this.detectedOverlappingOutputs = detectedOverlappingOutputs;
    }

    @Override
    public boolean isSuccessful() {
        return successful;
    }

    public void setSuccessful(boolean successful) {
        this.successful = successful;
    }

    /**
     * The fingerprints of the output files for the current execution.
     *
     * @return The fingerprint of the output files before or after the task executed, depending on which one is available.
     */
    @Override
    public ImmutableSortedMap<String, CurrentFileCollectionFingerprint> getOutputFingerprints() {
        return outputFingerprints;
    }

    public void setOutputFingerprintsAfterExecution(ImmutableSortedMap<String, CurrentFileCollectionFingerprint> outputFilesSnapshot) {
        this.outputFingerprints = outputFilesSnapshot;
    }

    @Override
    public ImmutableSortedMap<String, CurrentFileCollectionFingerprint> getInputFingerprints() {
        return inputFingerprints;
    }

    @Nullable
    public OverlappingOutputs getDetectedOverlappingOutputs() {
        return detectedOverlappingOutputs;
    }

    public HistoricalTaskExecution archive() {
        ImmutableSortedMap<String, HistoricalFileCollectionFingerprint> historicalInputFingerprints = ImmutableSortedMap.copyOfSorted(Maps.transformValues(inputFingerprints, ARCHIVE_FINGERPRINT));
        ImmutableSortedMap<String, HistoricalFileCollectionFingerprint> historicalOutputFingerprints = ImmutableSortedMap.copyOfSorted(Maps.transformValues(outputFingerprints, ARCHIVE_FINGERPRINT));
        return new HistoricalTaskExecution(
            getTaskImplementation(),
            getTaskActionImplementations(),
            getInputProperties(),
            getOutputPropertyNamesForCacheKey(),
            historicalInputFingerprints,
            historicalOutputFingerprints,
            successful,
            originExecutionMetadata
        );
    }

    @Override
    public OriginTaskExecutionMetadata getOriginExecutionMetadata() {
        return originExecutionMetadata;
    }

    public void setOriginExecutionMetadata(OriginTaskExecutionMetadata originExecutionMetadata) {
        this.originExecutionMetadata = originExecutionMetadata;
    }
}
