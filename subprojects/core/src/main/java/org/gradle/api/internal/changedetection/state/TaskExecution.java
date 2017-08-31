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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.ImmutableSortedSet;
import org.gradle.api.internal.OverlappingOutputs;
import org.gradle.internal.id.UniqueId;

/**
 * The state for a single task execution.
 */
public abstract class TaskExecution {
    private final UniqueId buildInvocationId;
    private final ImplementationSnapshot taskImplementation;
    private final ImmutableList<ImplementationSnapshot> taskActionImplementations;
    private final ImmutableSortedMap<String, ValueSnapshot> inputProperties;
    private final ImmutableSortedSet<String> outputPropertyNamesForCacheKey;
    private final ImmutableSet<String> declaredOutputFilePaths;
    private final OverlappingOutputs detectedOverlappingOutputs;
    private Boolean successful;

    public TaskExecution(
        UniqueId buildInvocationId,
        ImplementationSnapshot taskImplementation,
        ImmutableList<ImplementationSnapshot> taskActionImplementations,
        ImmutableSortedMap<String, ValueSnapshot> inputProperties,
        ImmutableSortedSet<String> outputPropertyNames,
        ImmutableSet<String> declaredOutputFilePaths,
        Boolean successful,
        OverlappingOutputs detectedOverlappingOutputs) {
        this.buildInvocationId = buildInvocationId;
        this.taskImplementation = taskImplementation;
        this.taskActionImplementations = taskActionImplementations;
        this.inputProperties = inputProperties;
        this.outputPropertyNamesForCacheKey = outputPropertyNames;
        this.declaredOutputFilePaths = declaredOutputFilePaths;
        this.successful = successful;
        this.detectedOverlappingOutputs = detectedOverlappingOutputs;
    }

    public Boolean isSuccessful() {
        return successful;
    }

    public void setSuccessful(Boolean successful) {
        this.successful = successful;
    }

    public UniqueId getBuildInvocationId() {
        return buildInvocationId;
    }

    /**
     * Returns the names of all cacheable output property names that have a value set.
     * The collection includes names of properties declared via mapped plural outputs,
     * and excludes optional properties that don't have a value set. If the task is not
     * cacheable, it returns an empty collection.
     */
    public ImmutableSortedSet<String> getOutputPropertyNamesForCacheKey() {
        return ImmutableSortedSet.copyOf(outputPropertyNamesForCacheKey);
    }

    /**
     * Returns the absolute path of every declared output file and directory.
     * The returned set includes potentially missing files as well, and does
     * not include the resolved contents of directories.
     */
    public ImmutableSet<String> getDeclaredOutputFilePaths() {
        return declaredOutputFilePaths;
    }

    public ImplementationSnapshot getTaskImplementation() {
        return taskImplementation;
    }

    public ImmutableList<ImplementationSnapshot> getTaskActionImplementations() {
        return taskActionImplementations;
    }

    public ImmutableSortedMap<String, ValueSnapshot> getInputProperties() {
        return inputProperties;
    }

    public abstract ImmutableSortedMap<String, FileCollectionSnapshot> getOutputFilesSnapshot();

    public abstract void setOutputFilesSnapshot(ImmutableSortedMap<String, FileCollectionSnapshot> outputFilesSnapshot);

    public abstract ImmutableSortedMap<String, FileCollectionSnapshot> getInputFilesSnapshot();

    public abstract FileCollectionSnapshot getDiscoveredInputFilesSnapshot();

    public abstract void setDiscoveredInputFilesSnapshot(FileCollectionSnapshot inputFilesSnapshot);

    public OverlappingOutputs getDetectedOverlappingOutputs() {
        return detectedOverlappingOutputs;
    }
}
