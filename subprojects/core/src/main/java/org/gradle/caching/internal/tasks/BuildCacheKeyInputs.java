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

import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.hash.HashCode;
import org.gradle.api.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Records the inputs which constitute the {@link TaskOutputCachingBuildCacheKey}.
 */
public class BuildCacheKeyInputs {
    private final String taskClass;
    private final HashCode classLoaderHash;
    private final List<HashCode> actionClassLoaderHashes;
    private final ImmutableSortedMap<String, HashCode> inputHashes;
    private final ImmutableSortedSet<String> outputPropertyNames;

    public BuildCacheKeyInputs(String taskClass, HashCode classLoaderHash, List<HashCode> actionClassLoaderHashes, ImmutableSortedMap<String, HashCode> inputHashes, ImmutableSortedSet<String> outputPropertyNames) {
        this.taskClass = taskClass;
        this.inputHashes = inputHashes;
        this.classLoaderHash = classLoaderHash;
        this.actionClassLoaderHashes = actionClassLoaderHashes;
        this.outputPropertyNames = outputPropertyNames;
    }

    public String getTaskClass() {
        return taskClass;
    }

    public Map<String, HashCode> getInputHashes() {
        return inputHashes;
    }

    @Nullable
    public HashCode getClassLoaderHash() {
        return classLoaderHash;
    }

    public List<HashCode> getActionClassLoaderHashes() {
        return actionClassLoaderHashes;
    }

    public Set<String> getOutputPropertyNames() {
        return outputPropertyNames;
    }

    @Override
    public String toString() {
        return "BuildCacheKeyInputs{"
            + "classLoaderHash=" + classLoaderHash
            + ", actionsClassLoaderHash=" + actionClassLoaderHashes
            + ", inputHashes=" + inputHashes
            + ", outputPropertyNames=" + outputPropertyNames
            + '}';
    }
}
