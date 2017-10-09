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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.ImmutableSortedSet;
import org.gradle.api.NonNullApi;
import org.gradle.api.internal.OverlappingOutputs;
import org.gradle.internal.id.UniqueId;

import javax.annotation.Nullable;

@NonNullApi
public class CurrentTaskExecution extends AbstractTaskExecution {
    private final ImmutableSet<String> declaredOutputFilePaths;
    private ImmutableSortedMap<String, FileCollectionSnapshot> outputFilesSnapshot;
    private final ImmutableSortedMap<String, FileCollectionSnapshot> inputFilesSnapshot;
    private FileCollectionSnapshot discoveredInputFilesSnapshot;
    private final OverlappingOutputs detectedOverlappingOutputs;
    private Boolean successful;

    public CurrentTaskExecution(
        UniqueId buildInvocationId,
        ImplementationSnapshot taskImplementation,
        ImmutableList<ImplementationSnapshot> taskActionImplementations,
        ImmutableSortedMap<String, ValueSnapshot> inputProperties,
        ImmutableSortedSet<String> outputPropertyNames,
        ImmutableSet<String> declaredOutputFilePaths,
        ImmutableSortedMap<String, FileCollectionSnapshot> inputFilesSnapshot,
        FileCollectionSnapshot discoveredInputFilesSnapshot,
        ImmutableSortedMap<String, FileCollectionSnapshot> outputFilesSnapshot,
        @Nullable OverlappingOutputs detectedOverlappingOutputs
    ) {
        super(buildInvocationId, taskImplementation, taskActionImplementations, inputProperties, outputPropertyNames);
        this.declaredOutputFilePaths = declaredOutputFilePaths;
        this.outputFilesSnapshot = outputFilesSnapshot;
        this.inputFilesSnapshot = inputFilesSnapshot;
        this.discoveredInputFilesSnapshot = discoveredInputFilesSnapshot;
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
    public ImmutableSortedMap<String, FileCollectionSnapshot> getOutputFilesSnapshot() {
        return outputFilesSnapshot;
    }

    public void setOutputFilesSnapshot(ImmutableSortedMap<String, FileCollectionSnapshot> outputFilesSnapshot) {
        this.outputFilesSnapshot = outputFilesSnapshot;
    }

    @Override
    public ImmutableSortedMap<String, FileCollectionSnapshot> getInputFilesSnapshot() {
        return inputFilesSnapshot;
    }

    @Override
    public FileCollectionSnapshot getDiscoveredInputFilesSnapshot() {
        return discoveredInputFilesSnapshot;
    }

    public void setDiscoveredInputFilesSnapshot(FileCollectionSnapshot discoveredInputFilesSnapshot) {
        this.discoveredInputFilesSnapshot = discoveredInputFilesSnapshot;
    }

    @Nullable
    public OverlappingOutputs getDetectedOverlappingOutputs() {
        return detectedOverlappingOutputs;
    }

    public HistoricalTaskExecution archive() {
        return new HistoricalTaskExecution(
            getBuildInvocationId(),
            getTaskImplementation(),
            getTaskActionImplementations(),
            getInputProperties(),
            getOutputPropertyNamesForCacheKey(),
            inputFilesSnapshot,
            discoveredInputFilesSnapshot,
            outputFilesSnapshot,
            successful
        );
    }
}
