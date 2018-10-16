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

package org.gradle.caching.internal.tasks;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.ImmutableSortedSet;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.snapshot.impl.ImplementationSnapshot;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Records the inputs which constitute the {@link TaskOutputCachingBuildCacheKey}.
 */
public class BuildCacheKeyInputs {

    private final ImplementationSnapshot taskImplementation;
    private final List<ImplementationSnapshot> actionImplementations;
    private final ImmutableSortedMap<String, HashCode> inputValueHashes;
    private final ImmutableSortedMap<String, CurrentFileCollectionFingerprint> inputFiles;
    private final ImmutableSortedMap<String, String> nonCacheableInputProperties;
    private final ImmutableSortedSet<String> outputPropertyNames;

    public BuildCacheKeyInputs(
        @Nullable ImplementationSnapshot taskImplementation,
        @Nullable ImmutableList<ImplementationSnapshot> actionImplementations,
        @Nullable ImmutableSortedMap<String, HashCode> inputValueHashes,
        @Nullable ImmutableSortedMap<String, CurrentFileCollectionFingerprint> inputFiles,
        @Nullable ImmutableSortedMap<String, String> nonCacheableInputProperties,
        @Nullable ImmutableSortedSet<String> outputPropertyNames
    ) {
        this.taskImplementation = taskImplementation;
        this.actionImplementations = actionImplementations;
        this.inputValueHashes = inputValueHashes;
        this.inputFiles = inputFiles;
        this.nonCacheableInputProperties = nonCacheableInputProperties;
        this.outputPropertyNames = outputPropertyNames;
    }

    @Nullable
    public ImplementationSnapshot getTaskImplementation() {
        return taskImplementation;
    }

    @Nullable
    public List<ImplementationSnapshot> getActionImplementations() {
        return actionImplementations;
    }

    @Nullable
    public ImmutableSortedMap<String, HashCode> getInputValueHashes() {
        return inputValueHashes;
    }

    @Nullable
    public ImmutableSortedMap<String, CurrentFileCollectionFingerprint> getInputFiles() {
        return inputFiles;
    }

    @Nullable
    public ImmutableSortedMap<String, String> getNonCacheableInputProperties() {
        return nonCacheableInputProperties;
    }

    @Nullable
    public ImmutableSortedSet<String> getOutputPropertyNames() {
        return outputPropertyNames;
    }

    @Override
    public String toString() {
        return "BuildCacheKeyInputs{"
            + "taskImplementation=" + taskImplementation
            + ", actionImplementations=" + actionImplementations
            + ", inputValueHashes=" + inputValueHashes
            + ", inputFiles=" + inputFiles
            + ", nonCacheableInputProperties=" + nonCacheableInputProperties
            + ", outputPropertyNames=" + outputPropertyNames
            + '}';
    }
}
