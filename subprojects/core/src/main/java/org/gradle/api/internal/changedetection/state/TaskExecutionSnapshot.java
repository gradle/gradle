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

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.hash.HashCode;

import java.util.List;

/**
 * Immutable snapshot of the state of a task when it was executed.
 */
public class TaskExecutionSnapshot {
    private final String taskClass;
    private final HashCode taskClassLoaderHash;
    private final List<HashCode> taskActionsClassLoaderHashes;
    private final ImmutableSortedMap<String, ValueSnapshot> inputProperties;
    private final ImmutableSortedSet<String> cacheableOutputProperties;
    private final ImmutableSet<String> declaredOutputFilePaths;
    private final ImmutableSortedMap<String, Long> inputFilesSnapshotIds;
    private final ImmutableSortedMap<String, Long> outputFilesSnapshotIds;
    private final Long discoveredFilesSnapshotId;

    public TaskExecutionSnapshot(String taskClass, ImmutableSortedSet<String> cacheableOutputProperties, ImmutableSet<String> declaredOutputFilePaths, HashCode taskClassLoaderHash, List<HashCode> taskActionsClassLoaderHashes, ImmutableSortedMap<String, ValueSnapshot> inputProperties, ImmutableSortedMap<String, Long> inputFilesSnapshotIds, Long discoveredFilesSnapshotId, ImmutableSortedMap<String, Long> outputFilesSnapshotIds) {
        this.taskClass = taskClass;
        this.cacheableOutputProperties = cacheableOutputProperties;
        this.declaredOutputFilePaths = declaredOutputFilePaths;
        this.taskClassLoaderHash = taskClassLoaderHash;
        this.taskActionsClassLoaderHashes = taskActionsClassLoaderHashes;
        this.inputProperties = inputProperties;
        this.inputFilesSnapshotIds = inputFilesSnapshotIds;
        this.discoveredFilesSnapshotId = discoveredFilesSnapshotId;
        this.outputFilesSnapshotIds = outputFilesSnapshotIds;
    }

    public ImmutableSortedSet<String> getCacheableOutputProperties() {
        return cacheableOutputProperties;
    }

    public ImmutableSet<String> getDeclaredOutputFilePaths() {
        return declaredOutputFilePaths;
    }

    public Long getDiscoveredFilesSnapshotId() {
        return discoveredFilesSnapshotId;
    }

    public ImmutableSortedMap<String, Long> getInputFilesSnapshotIds() {
        return inputFilesSnapshotIds;
    }

    public ImmutableSortedMap<String, ValueSnapshot> getInputProperties() {
        return inputProperties;
    }

    public ImmutableSortedMap<String, Long> getOutputFilesSnapshotIds() {
        return outputFilesSnapshotIds;
    }

    public List<HashCode> getTaskActionsClassLoaderHashes() {
        return taskActionsClassLoaderHashes;
    }

    public String getTaskClass() {
        return taskClass;
    }

    public HashCode getTaskClassLoaderHash() {
        return taskClassLoaderHash;
    }
}
