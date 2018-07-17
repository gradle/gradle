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
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Maps;
import org.gradle.api.NonNullApi;
import org.gradle.api.internal.OverlappingOutputs;
import org.gradle.api.internal.changedetection.state.mirror.logical.CurrentFileCollectionFingerprint;
import org.gradle.api.internal.changedetection.state.mirror.logical.HistoricalFileCollectionFingerprint;
import org.gradle.api.internal.tasks.OriginTaskExecutionMetadata;

import javax.annotation.Nullable;

@NonNullApi
public class CurrentTaskExecution extends AbstractTaskExecution {

    private static final Function<FileCollectionSnapshot, HistoricalFileCollectionFingerprint> ARCHIVE_FINGERPRINT = new Function<FileCollectionSnapshot, HistoricalFileCollectionFingerprint>() {
        @Override
        @SuppressWarnings("NullableProblems")
        public HistoricalFileCollectionFingerprint apply(FileCollectionSnapshot value) {
            return value.archive();
        }
    };

    private final ImmutableSet<String> declaredOutputFilePaths;
    private ImmutableSortedMap<String, ? extends FileCollectionSnapshot> outputFilesSnapshot;
    private final ImmutableSortedMap<String, CurrentFileCollectionFingerprint> inputFilesSnapshot;
    private final OverlappingOutputs detectedOverlappingOutputs;
    private Boolean successful;
    private OriginTaskExecutionMetadata originExecutionMetadata;

    public CurrentTaskExecution(
        ImplementationSnapshot taskImplementation,
        ImmutableList<ImplementationSnapshot> taskActionImplementations,
        ImmutableSortedMap<String, ValueSnapshot> inputProperties,
        ImmutableSortedSet<String> outputPropertyNames,
        ImmutableSet<String> declaredOutputFilePaths,
        ImmutableSortedMap<String, CurrentFileCollectionFingerprint> inputFilesSnapshot,
        ImmutableSortedMap<String, CurrentFileCollectionFingerprint> outputFilesSnapshot,
        @Nullable OverlappingOutputs detectedOverlappingOutputs
    ) {
        super(taskImplementation, taskActionImplementations, inputProperties, outputPropertyNames);
        this.declaredOutputFilePaths = declaredOutputFilePaths;
        this.outputFilesSnapshot = outputFilesSnapshot;
        this.inputFilesSnapshot = inputFilesSnapshot;
        this.detectedOverlappingOutputs = detectedOverlappingOutputs;
    }

    /**
     * Returns the absolute path of every declared output file and directory.
     * The returned set includes potentially missing files as well, and does
     * not include the resolved contents of directories.
     */
    public ImmutableSet<String> getDeclaredOutputFilePaths() {
        return declaredOutputFilePaths;
    }

    @Override
    public boolean isSuccessful() {
        return successful;
    }

    public void setSuccessful(boolean successful) {
        this.successful = successful;
    }


    @Override
    public ImmutableSortedMap<String, ? extends FileCollectionSnapshot> getOutputFilesSnapshot() {
        return outputFilesSnapshot;
    }

    public void setOutputFilesSnapshot(ImmutableSortedMap<String, ? extends FileCollectionSnapshot> outputFilesSnapshot) {
        this.outputFilesSnapshot = outputFilesSnapshot;
    }

    @Override
    public ImmutableSortedMap<String, CurrentFileCollectionFingerprint> getInputFilesSnapshot() {
        return inputFilesSnapshot;
    }

    @Nullable
    public OverlappingOutputs getDetectedOverlappingOutputs() {
        return detectedOverlappingOutputs;
    }

    public HistoricalTaskExecution archive() {
        ImmutableSortedMap<String, HistoricalFileCollectionFingerprint> historicalInputFingerprints = ImmutableSortedMap.copyOfSorted(Maps.transformValues(inputFilesSnapshot, ARCHIVE_FINGERPRINT));
        ImmutableSortedMap<String, HistoricalFileCollectionFingerprint> historicalOutputFingerprints = ImmutableSortedMap.copyOfSorted(Maps.transformValues(outputFilesSnapshot, ARCHIVE_FINGERPRINT));
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
