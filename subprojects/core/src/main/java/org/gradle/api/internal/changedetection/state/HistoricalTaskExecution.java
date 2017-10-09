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
import com.google.common.collect.ImmutableSortedSet;
import org.gradle.api.NonNullApi;
import org.gradle.internal.id.UniqueId;

/**
 * State of a task when it was executed.
 */
@NonNullApi
public class HistoricalTaskExecution extends AbstractTaskExecution {
    private final boolean successful;
    private final ImmutableSortedMap<String, FileCollectionSnapshot> inputFilesSnapshot;
    private final FileCollectionSnapshot discoveredInputFilesSnapshot;
    private final ImmutableSortedMap<String, FileCollectionSnapshot> outputFilesSnapshot;

    public HistoricalTaskExecution(
        UniqueId buildInvocationId,
        ImplementationSnapshot taskImplementation,
        ImmutableList<ImplementationSnapshot> taskActionsImplementations,
        ImmutableSortedMap<String, ValueSnapshot> inputProperties,
        ImmutableSortedSet<String> outputPropertyNames,
        ImmutableSortedMap<String, FileCollectionSnapshot> inputFilesSnapshot,
        FileCollectionSnapshot discoveredInputFilesSnapshot,
        ImmutableSortedMap<String, FileCollectionSnapshot> outputFilesSnapshot,
        boolean successful
    ) {
        super(buildInvocationId, taskImplementation, taskActionsImplementations, inputProperties, outputPropertyNames);
        this.inputFilesSnapshot = inputFilesSnapshot;
        this.discoveredInputFilesSnapshot = discoveredInputFilesSnapshot;
        this.outputFilesSnapshot = outputFilesSnapshot;
        this.successful = successful;
    }

    @Override
    public boolean isSuccessful() {
        return successful;
    }

    @Override
    public ImmutableSortedMap<String, FileCollectionSnapshot> getInputFilesSnapshot() {
        return inputFilesSnapshot;
    }

    @Override
    public FileCollectionSnapshot getDiscoveredInputFilesSnapshot() {
        return discoveredInputFilesSnapshot;
    }

    @Override
    public ImmutableSortedMap<String, FileCollectionSnapshot> getOutputFilesSnapshot() {
        return outputFilesSnapshot;
    }
}
