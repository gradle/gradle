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

package org.gradle.api.internal.tasks.execution;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.gradle.api.Task;
import org.gradle.caching.BuildCacheKey;

import java.util.Map;
import java.util.Set;

public interface TaskCachingHashesListener {
    void inputsCollected(Task task, BuildCacheKey key, TaskCachingInputs hashes);

    class TaskCachingInputs {
        private final Map<String, byte[]> inputHashes;
        private final byte[] classLoaderHash;
        private final byte[] actionsClassLoaderHash;
        private final Set<String> outputPropertyNames;

        public TaskCachingInputs(byte[] classLoaderHash, byte[] actionsClassLoaderHash, Map<String, byte[]> inputHashes, Set<String> outputPropertyNames) {
            this.inputHashes = ImmutableMap.copyOf(inputHashes);
            this.classLoaderHash = classLoaderHash;
            this.actionsClassLoaderHash = actionsClassLoaderHash;
            this.outputPropertyNames = ImmutableSet.copyOf(outputPropertyNames);
        }

        public Map<String, byte[]> getInputHashes() {
            return inputHashes;
        }

        public byte[] getClassLoaderHash() {
            return classLoaderHash;
        }

        public byte[] getActionsClassLoaderHash() {
            return actionsClassLoaderHash;
        }

        public Set<String> getOutputPropertyNames() {
            return outputPropertyNames;
        }
    }
}
