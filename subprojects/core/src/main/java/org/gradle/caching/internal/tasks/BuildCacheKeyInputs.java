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

import com.google.common.hash.HashCode;
import org.gradle.api.Nullable;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * Records the inputs which constitute the {@link TaskOutputCachingBuildCacheKey}.
 */
public class BuildCacheKeyInputs {
    private final String taskClass;
    private final HashCode classLoaderHash;
    private final HashCode actionsClassLoaderHash;
    private final Map<String, HashCode> inputHashes;
    private final Set<String> outputPropertyNames;

    public BuildCacheKeyInputs(String taskClass, HashCode classLoaderHash, HashCode actionsClassLoaderHash, Map<String, HashCode> inputHashes, Set<String> outputPropertyNames) {
        this.taskClass = taskClass;
        this.inputHashes = Collections.unmodifiableMap(inputHashes);
        this.classLoaderHash = classLoaderHash;
        this.actionsClassLoaderHash = actionsClassLoaderHash;
        this.outputPropertyNames = Collections.unmodifiableSet(outputPropertyNames);
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

    @Nullable
    public HashCode getActionsClassLoaderHash() {
        return actionsClassLoaderHash;
    }

    public Set<String> getOutputPropertyNames() {
        return outputPropertyNames;
    }

    @Override
    public String toString() {
        return "BuildCacheKeyInputs{"
            + "classLoaderHash=" + classLoaderHash
            + ", actionsClassLoaderHash=" + actionsClassLoaderHash
            + ", inputHashes=" + inputHashes
            + ", outputPropertyNames=" + outputPropertyNames
            + '}';
    }
}
