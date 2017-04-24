/*
 * Copyright 2011 the original author or authors.
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
import org.gradle.api.internal.TaskExecutionHistory;

import java.util.List;

/**
 * The state for a single task execution.
 */
public abstract class TaskExecution {
    private String taskClass;
    private HashCode taskClassLoaderHash;
    private List<HashCode> taskActionsClassLoaderHashes;
    private ImmutableSortedMap<String, ValueSnapshot> inputProperties;
    private Iterable<String> outputPropertyNamesForCacheKey;
    private ImmutableSet<String> declaredOutputFilePaths;
    private TaskExecutionHistory.OverlappingOutputs detectedOverlappingOutputs;

    /**
     * Returns the names of all cacheable output property names that have a value set.
     * The collection includes names of properties declared via mapped plural outputs,
     * and excludes optional properties that don't have a value set. If the task is not
     * cacheable, it returns an empty collection.
     */
    public ImmutableSortedSet<String> getOutputPropertyNamesForCacheKey() {
        return ImmutableSortedSet.copyOf(outputPropertyNamesForCacheKey);
    }

    public void setOutputPropertyNamesForCacheKey(Iterable<String> outputPropertyNames) {
        this.outputPropertyNamesForCacheKey = outputPropertyNames;
    }

    /**
     * Returns the absolute path of every declared output file and directory.
     * The returned set includes potentially missing files as well, and does
     * not include the resolved contents of directories.
     */
    public ImmutableSet<String> getDeclaredOutputFilePaths() {
        return declaredOutputFilePaths;
    }

    public void setDeclaredOutputFilePaths(ImmutableSet<String> declaredOutputFilePaths) {
        this.declaredOutputFilePaths = declaredOutputFilePaths;
    }

    public String getTaskClass() {
        return taskClass;
    }

    public void setTaskClass(String taskClass) {
        this.taskClass = taskClass;
    }

    public HashCode getTaskClassLoaderHash() {
        return taskClassLoaderHash;
    }

    public void setTaskClassLoaderHash(HashCode taskClassLoaderHash) {
        this.taskClassLoaderHash = taskClassLoaderHash;
    }

    public List<HashCode> getTaskActionsClassLoaderHashes() {
        return taskActionsClassLoaderHashes;
    }

    public void setTaskActionsClassLoaderHashes(List<HashCode> taskActionsClassLoaderHashes) {
        this.taskActionsClassLoaderHashes = taskActionsClassLoaderHashes;
    }

    public ImmutableSortedMap<String, ValueSnapshot> getInputProperties() {
        return inputProperties;
    }

    public void setInputProperties(ImmutableSortedMap<String, ValueSnapshot> inputProperties) {
        this.inputProperties = inputProperties;
    }

    /**
     * @return May return null.
     */
    public abstract ImmutableSortedMap<String, FileCollectionSnapshot> getOutputFilesSnapshot();

    public abstract void setOutputFilesSnapshot(ImmutableSortedMap<String, FileCollectionSnapshot> outputFilesSnapshot);

    public abstract ImmutableSortedMap<String, FileCollectionSnapshot> getInputFilesSnapshot();

    public abstract void setInputFilesSnapshot(ImmutableSortedMap<String, FileCollectionSnapshot> inputFilesSnapshot);

    public abstract FileCollectionSnapshot getDiscoveredInputFilesSnapshot();

    public abstract void setDiscoveredInputFilesSnapshot(FileCollectionSnapshot inputFilesSnapshot);

    public TaskExecutionHistory.OverlappingOutputs getDetectedOverlappingOutputs() {
        return detectedOverlappingOutputs;
    }

    public void setDetectedOverlappingOutputs(TaskExecutionHistory.OverlappingOutputs detectedOverlappingOutputs) {
        this.detectedOverlappingOutputs = detectedOverlappingOutputs;
    }
}
