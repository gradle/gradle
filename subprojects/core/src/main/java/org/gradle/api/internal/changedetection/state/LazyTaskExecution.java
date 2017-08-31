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
import org.gradle.api.internal.OverlappingOutputs;
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
            null
        );
        this.inputFilesSnapshotIds = taskExecutionSnapshot.getInputFilesSnapshotIds();
        this.discoveredFilesSnapshotId = taskExecutionSnapshot.getDiscoveredFilesSnapshotId();
        this.outputFilesSnapshotIds = taskExecutionSnapshot.getOutputFilesSnapshotIds();
    }

    public LazyTaskExecution(
        FileSnapshotRepository snapshotRepository,
        UniqueId buildInvocationId,
        ImplementationSnapshot taskImplementation,
        ImmutableList<ImplementationSnapshot> taskActionsImplementations,
        ImmutableSortedMap<String, ValueSnapshot> inputProperties,
        ImmutableSortedSet<String> outputPropertyNames,
        ImmutableSet<String> declaredOutputFilePaths,
        ImmutableSortedMap<String, FileCollectionSnapshot> inputFilesSnapshot,
        FileCollectionSnapshot discoveredFilesSnapshot,
        ImmutableSortedMap<String, FileCollectionSnapshot> outputFilesSnapshot,
        OverlappingOutputs detectedOverlappingOutputs
    ) {
        this(
            snapshotRepository,
            buildInvocationId,
            taskImplementation,
            taskActionsImplementations,
            inputProperties,
            outputPropertyNames,
            declaredOutputFilePaths,
            null,
            detectedOverlappingOutputs
        );
        this.inputFilesSnapshot = inputFilesSnapshot;
        this.discoveredFilesSnapshot = discoveredFilesSnapshot;
        this.outputFilesSnapshot = outputFilesSnapshot;
    }

    private LazyTaskExecution(
        FileSnapshotRepository snapshotRepository,
        UniqueId buildInvocationId,
        ImplementationSnapshot taskImplementation,
        ImmutableList<ImplementationSnapshot> taskActionImplementations,
        ImmutableSortedMap<String, ValueSnapshot> inputProperties,
        ImmutableSortedSet<String> outputPropertyNames,
        ImmutableSet<String> declaredOutputFilePaths,
        Boolean successful,
        OverlappingOutputs detectedOverlappingOutputs
    ) {
        super(
            buildInvocationId,
            taskImplementation,
            taskActionImplementations,
            inputProperties,
            outputPropertyNames,
            declaredOutputFilePaths,
            successful,
            detectedOverlappingOutputs
        );
        this.snapshotRepository = snapshotRepository;
    }

    @Override
    public ImmutableSortedMap<String, FileCollectionSnapshot> getInputFilesSnapshot() {
        if (inputFilesSnapshot == null) {
            inputFilesSnapshot = loadSnapshot(inputFilesSnapshotIds);
        }
        return inputFilesSnapshot;
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
            outputFilesSnapshot = loadSnapshot(outputFilesSnapshotIds);
        }
        return outputFilesSnapshot;
    }

    private ImmutableSortedMap<String, FileCollectionSnapshot> loadSnapshot(Map<String, Long> snapshotIds) {
        ImmutableSortedMap.Builder<String, FileCollectionSnapshot> builder = ImmutableSortedMap.naturalOrder();
        for (Map.Entry<String, Long> entry : snapshotIds.entrySet()) {
            String propertyName = entry.getKey();
            Long snapshotId = entry.getValue();
            FileCollectionSnapshot fileCollectionSnapshot = snapshotRepository.get(snapshotId);
            builder.put(propertyName, fileCollectionSnapshot);
        }
        return builder.build();
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
            inputFilesSnapshotIds = storeSnapshot(inputFilesSnapshot);
        }
        if (outputFilesSnapshotIds == null && outputFilesSnapshot != null) {
            outputFilesSnapshotIds = storeSnapshot(outputFilesSnapshot);
        }
        if (discoveredFilesSnapshotId == null && discoveredFilesSnapshot != null) {
            discoveredFilesSnapshotId = snapshotRepository.add(discoveredFilesSnapshot);
        }
    }

    private ImmutableSortedMap<String, Long> storeSnapshot(Map<String, FileCollectionSnapshot> snapshot) {
        ImmutableSortedMap.Builder<String, Long> builder = ImmutableSortedMap.naturalOrder();
        for (Map.Entry<String, FileCollectionSnapshot> entry : snapshot.entrySet()) {
            String propertyName = entry.getKey();
            FileCollectionSnapshot fileCollectionSnapshot = entry.getValue();
            Long snapshotId = snapshotRepository.add(fileCollectionSnapshot);
            builder.put(propertyName, snapshotId);
        }
        return builder.build();
    }

    public void removeUnnecessarySnapshots() {
        if (inputFilesSnapshotIds != null) {
            removeUnnecessarySnapshot(inputFilesSnapshotIds);
            inputFilesSnapshotIds = null;
        }
        if (outputFilesSnapshotIds != null) {
            removeUnnecessarySnapshot(outputFilesSnapshotIds);
            outputFilesSnapshotIds = null;
        }
        if (discoveredFilesSnapshotId != null) {
            snapshotRepository.remove(discoveredFilesSnapshotId);
            discoveredFilesSnapshotId = null;
        }
    }

    private void removeUnnecessarySnapshot(Map<String, Long> snapshot) {
        for (Long id : snapshot.values()) {
            snapshotRepository.remove(id);
        }
    }
}
