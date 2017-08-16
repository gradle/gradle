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
import org.gradle.internal.id.UniqueId;

import java.util.Map;

public class LazyTaskExecution extends TaskExecution {
    private ImmutableSortedMap<String, Long> inputFilesSnapshotIds;
    private ImmutableSortedMap<String, Long> outputFilesSnapshotIds;
    private Long discoveredFilesSnapshotId;
    private final FileSnapshotRepository snapshotRepository;
    private ImmutableSortedMap<String, FileCollectionSnapshot> inputFilesSnapshot;
    private ImmutableSortedMap<String, FileCollectionSnapshot> outputFilesSnapshot;
    private FileCollectionSnapshot discoveredFilesSnapshot;

    /**
     * Creates a mutable copy of the given snapshot.
     */
    LazyTaskExecution(TaskExecutionSnapshot taskExecutionSnapshot, FileSnapshotRepository snapshotRepository) {
        this(
            snapshotRepository,
            taskExecutionSnapshot.getBuildInvocationId(),
            taskExecutionSnapshot.getTaskImplementation(),
            taskExecutionSnapshot.getTaskActionsImplementations(),
            taskExecutionSnapshot.getInputProperties(),
            taskExecutionSnapshot.getCacheableOutputProperties(),
            taskExecutionSnapshot.getDeclaredOutputFilePaths()
        );
        setSuccessful(taskExecutionSnapshot.isSuccessful());
        this.setInputFilesSnapshotIds(taskExecutionSnapshot.getInputFilesSnapshotIds());
        this.setOutputFilesSnapshotIds(taskExecutionSnapshot.getOutputFilesSnapshotIds());
        this.setDiscoveredFilesSnapshotId(taskExecutionSnapshot.getDiscoveredFilesSnapshotId());
    }

    public LazyTaskExecution(
        FileSnapshotRepository snapshotRepository,
        UniqueId buildInvocationId,
        ImplementationSnapshot taskImplementation,
        ImmutableList<ImplementationSnapshot> taskActionsImplementations,
        ImmutableSortedMap<String, ValueSnapshot> inputProperties,
        ImmutableSortedSet<String> outputPropertyNames,
        ImmutableSet<String> declaredOutputFilePaths
    ) {
        super(buildInvocationId, taskImplementation, taskActionsImplementations, inputProperties, outputPropertyNames, declaredOutputFilePaths);
        this.snapshotRepository = snapshotRepository;
    }

    @Override
    public ImmutableSortedMap<String, FileCollectionSnapshot> getInputFilesSnapshot() {
        if (inputFilesSnapshot == null) {
            ImmutableSortedMap.Builder<String, FileCollectionSnapshot> builder = ImmutableSortedMap.naturalOrder();
            for (Map.Entry<String, Long> entry : getInputFilesSnapshotIds().entrySet()) {
                builder.put(entry.getKey(), getSnapshotRepository().get(entry.getValue()));
            }
            inputFilesSnapshot = builder.build();
        }
        return inputFilesSnapshot;
    }

    @Override
    public void setInputFilesSnapshot(ImmutableSortedMap<String, FileCollectionSnapshot> inputFilesSnapshot) {
        this.inputFilesSnapshot = inputFilesSnapshot;
        this.setInputFilesSnapshotIds(null);
    }

    @Override
    public FileCollectionSnapshot getDiscoveredInputFilesSnapshot() {
        if (getDiscoveredFilesSnapshot() == null) {
            setDiscoveredFilesSnapshot(getSnapshotRepository().get(getDiscoveredFilesSnapshotId()));
        }
        return getDiscoveredFilesSnapshot();
    }

    @Override
    public void setDiscoveredInputFilesSnapshot(FileCollectionSnapshot discoveredFilesSnapshot) {
        this.setDiscoveredFilesSnapshot(discoveredFilesSnapshot);
        this.setDiscoveredFilesSnapshotId(null);
    }

    @Override
    public ImmutableSortedMap<String, FileCollectionSnapshot> getOutputFilesSnapshot() {
        if (outputFilesSnapshot == null) {
            ImmutableSortedMap.Builder<String, FileCollectionSnapshot> builder = ImmutableSortedMap.naturalOrder();
            for (Map.Entry<String, Long> entry : getOutputFilesSnapshotIds().entrySet()) {
                String propertyName = entry.getKey();
                builder.put(propertyName, getSnapshotRepository().get(entry.getValue()));
            }
            outputFilesSnapshot = builder.build();
        }
        return outputFilesSnapshot;
    }

    @Override
    public void setOutputFilesSnapshot(ImmutableSortedMap<String, FileCollectionSnapshot> outputFilesSnapshot) {
        this.outputFilesSnapshot = outputFilesSnapshot;
        setOutputFilesSnapshotIds(null);
    }

    public TaskExecutionSnapshot snapshot() {
        return new TaskExecutionSnapshot(
            isSuccessful(),
            getBuildInvocationId(),
            getTaskImplementation(),
            getTaskActionImplementations(),
            getOutputPropertyNamesForCacheKey(),
            getDeclaredOutputFilePaths(),
            getInputProperties(),
            getInputFilesSnapshotIds(),
            getDiscoveredFilesSnapshotId(),
            getOutputFilesSnapshotIds()
        );
    }

    public ImmutableSortedMap<String, Long> getInputFilesSnapshotIds() {
        return inputFilesSnapshotIds;
    }

    public void setInputFilesSnapshotIds(ImmutableSortedMap<String, Long> inputFilesSnapshotIds) {
        this.inputFilesSnapshotIds = inputFilesSnapshotIds;
    }

    public ImmutableSortedMap<String, Long> getOutputFilesSnapshotIds() {
        return outputFilesSnapshotIds;
    }

    public void setOutputFilesSnapshotIds(ImmutableSortedMap<String, Long> outputFilesSnapshotIds) {
        this.outputFilesSnapshotIds = outputFilesSnapshotIds;
    }

    public Long getDiscoveredFilesSnapshotId() {
        return discoveredFilesSnapshotId;
    }

    public void setDiscoveredFilesSnapshotId(Long discoveredFilesSnapshotId) {
        this.discoveredFilesSnapshotId = discoveredFilesSnapshotId;
    }

    public FileSnapshotRepository getSnapshotRepository() {
        return snapshotRepository;
    }

    public FileCollectionSnapshot getDiscoveredFilesSnapshot() {
        return discoveredFilesSnapshot;
    }

    public void setDiscoveredFilesSnapshot(FileCollectionSnapshot discoveredFilesSnapshot) {
        this.discoveredFilesSnapshot = discoveredFilesSnapshot;
    }

}
