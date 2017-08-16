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
    public LazyTaskExecution(FileSnapshotRepository snapshotRepository, TaskExecutionSnapshot taskExecutionSnapshot) {
        this(
            snapshotRepository,
            taskExecutionSnapshot.getBuildInvocationId(),
            taskExecutionSnapshot.getTaskImplementation(),
            taskExecutionSnapshot.getTaskActionsImplementations(),
            taskExecutionSnapshot.getInputProperties(),
            taskExecutionSnapshot.getCacheableOutputProperties(),
            taskExecutionSnapshot.getDeclaredOutputFilePaths(),
            taskExecutionSnapshot.isSuccessful(),
            taskExecutionSnapshot.getInputFilesSnapshotIds(),
            taskExecutionSnapshot.getDiscoveredFilesSnapshotId(),
            taskExecutionSnapshot.getOutputFilesSnapshotIds()
        );
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
        this(
            snapshotRepository,
            buildInvocationId,
            taskImplementation,
            taskActionsImplementations,
            inputProperties,
            outputPropertyNames,
            declaredOutputFilePaths,
            false,
            null,
            null,
            null
        );
    }

    private LazyTaskExecution(
        FileSnapshotRepository snapshotRepository,
        UniqueId buildInvocationId,
        ImplementationSnapshot taskImplementation,
        ImmutableList<ImplementationSnapshot> taskActionsImplementations,
        ImmutableSortedMap<String, ValueSnapshot> inputProperties,
        ImmutableSortedSet<String> outputPropertyNames,
        ImmutableSet<String> declaredOutputFilePaths,
        boolean successful,
        ImmutableSortedMap<String, Long> inputFilesSnapshotIds,
        Long discoveredFilesSnapshotId,
        ImmutableSortedMap<String, Long> outputFilesSnapshotIds
    ) {
        super(
            buildInvocationId,
            taskImplementation,
            taskActionsImplementations,
            inputProperties,
            outputPropertyNames,
            declaredOutputFilePaths,
            successful
        );
        this.snapshotRepository = snapshotRepository;
        this.inputFilesSnapshotIds = inputFilesSnapshotIds;
        this.discoveredFilesSnapshotId = discoveredFilesSnapshotId;
        this.outputFilesSnapshotIds = outputFilesSnapshotIds;
    }

    @Override
    public ImmutableSortedMap<String, FileCollectionSnapshot> getInputFilesSnapshot() {
        if (inputFilesSnapshot == null) {
            ImmutableSortedMap.Builder<String, FileCollectionSnapshot> builder = ImmutableSortedMap.naturalOrder();
            for (Map.Entry<String, Long> entry : inputFilesSnapshotIds.entrySet()) {
                builder.put(entry.getKey(), snapshotRepository.get(entry.getValue()));
            }
            inputFilesSnapshot = builder.build();
        }
        return inputFilesSnapshot;
    }

    @Override
    public void setInputFilesSnapshot(ImmutableSortedMap<String, FileCollectionSnapshot> inputFilesSnapshot) {
        this.inputFilesSnapshot = inputFilesSnapshot;
        this.inputFilesSnapshotIds = null;
    }

    @Override
    public FileCollectionSnapshot getDiscoveredInputFilesSnapshot() {
        if (discoveredFilesSnapshot == null) {
            discoveredFilesSnapshot = snapshotRepository.get(discoveredFilesSnapshotId);
        }
        return discoveredFilesSnapshot;
    }

    @Override
    public void setDiscoveredInputFilesSnapshot(FileCollectionSnapshot discoveredFilesSnapshot) {
        this.discoveredFilesSnapshot = discoveredFilesSnapshot;
        this.discoveredFilesSnapshotId = null;
    }

    @Override
    public ImmutableSortedMap<String, FileCollectionSnapshot> getOutputFilesSnapshot() {
        if (outputFilesSnapshot == null) {
            ImmutableSortedMap.Builder<String, FileCollectionSnapshot> builder = ImmutableSortedMap.naturalOrder();
            for (Map.Entry<String, Long> entry : outputFilesSnapshotIds.entrySet()) {
                String propertyName = entry.getKey();
                builder.put(propertyName, snapshotRepository.get(entry.getValue()));
            }
            outputFilesSnapshot = builder.build();
        }
        return outputFilesSnapshot;
    }

    @Override
    public void setOutputFilesSnapshot(ImmutableSortedMap<String, FileCollectionSnapshot> outputFilesSnapshot) {
        this.outputFilesSnapshot = outputFilesSnapshot;
        this.outputFilesSnapshotIds = null;
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
            inputFilesSnapshotIds,
            discoveredFilesSnapshotId,
            outputFilesSnapshotIds
        );
    }

    public void storeSnapshots() {
        if (inputFilesSnapshotIds == null && inputFilesSnapshot != null) {
            ImmutableSortedMap.Builder<String, Long> builder = ImmutableSortedMap.naturalOrder();
            for (Map.Entry<String, FileCollectionSnapshot> entry : inputFilesSnapshot.entrySet()) {
                builder.put(entry.getKey(), snapshotRepository.add(entry.getValue()));
            }
            inputFilesSnapshotIds = builder.build();
        }
        if (outputFilesSnapshotIds == null && outputFilesSnapshot != null) {
            ImmutableSortedMap.Builder<String, Long> builder = ImmutableSortedMap.naturalOrder();
            for (Map.Entry<String, FileCollectionSnapshot> entry : outputFilesSnapshot.entrySet()) {
                builder.put(entry.getKey(), snapshotRepository.add(entry.getValue()));
            }
            outputFilesSnapshotIds = builder.build();
        }
        if (discoveredFilesSnapshotId == null && discoveredFilesSnapshot != null) {
            discoveredFilesSnapshotId = snapshotRepository.add(discoveredFilesSnapshot);
        }
    }

    public void removeUnnecessarySnapshots() {
        if (inputFilesSnapshotIds != null) {
            for (Long id : inputFilesSnapshotIds.values()) {
                snapshotRepository.remove(id);
            }
        }
        if (outputFilesSnapshotIds != null) {
            for (Long id : outputFilesSnapshotIds.values()) {
                snapshotRepository.remove(id);
            }
        }
        if (discoveredFilesSnapshotId != null) {
            snapshotRepository.remove(discoveredFilesSnapshotId);
        }
    }
}
