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
import org.gradle.api.internal.tasks.execution.BuildCacheKeyInputs;
import org.gradle.caching.internal.DefaultBuildCacheKeyBuilder;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class DefaultTaskOutputCachingBuildCacheKeyBuilder {
    private DefaultBuildCacheKeyBuilder builder = new DefaultBuildCacheKeyBuilder();
    private Map<String, HashCode> inputHashes = new HashMap<String, HashCode>();
    private Set<String> outputPropertyNames = new HashSet<String>();
    private HashCode classLoaderHash;
    private HashCode actionsClassLoaderHash;

    public DefaultTaskOutputCachingBuildCacheKeyBuilder appendClassloaderHash(@Nullable HashCode hashCode) {
        classLoaderHash = hashCode;
        if (hashCode != null) {
            builder.putBytes(hashCode.asBytes());
        }
        return this;
    }

    public DefaultTaskOutputCachingBuildCacheKeyBuilder appendActionsClassloaderHash(@Nullable HashCode hashCode) {
        actionsClassLoaderHash = hashCode;
        if (hashCode != null) {
            builder.putBytes(hashCode.asBytes());
        }
        return this;
    }

    public DefaultTaskOutputCachingBuildCacheKeyBuilder appendInputPropertyHash(String propertyName, HashCode hashCode) {
        builder.putString(propertyName);
        builder.putBytes(hashCode.asBytes());
        inputHashes.put(propertyName, hashCode);
        return this;
    }

    public DefaultTaskOutputCachingBuildCacheKeyBuilder appendOutputPropertyName(String propertyName) {
        outputPropertyNames.add(propertyName);
        builder.putString(propertyName);
        return this;
    }

    public DefaultTaskOutputCachingBuildCacheKeyBuilder appendTaskClass(String taskClass) {
        builder.putString(taskClass);
        return this;
    }

    public TaskOutputCachingBuildCacheKey build() {
        return new DefaultTaskOutputCachingBuildCacheKey(
            (classLoaderHash == null || actionsClassLoaderHash == null) ? null : builder.build().getHashCode(),
            new BuildCacheKeyInputs(classLoaderHash, actionsClassLoaderHash, inputHashes, outputPropertyNames)
        );
    }

    private class DefaultTaskOutputCachingBuildCacheKey implements TaskOutputCachingBuildCacheKey {
        private final String hashCode;
        private final BuildCacheKeyInputs inputs;

        private DefaultTaskOutputCachingBuildCacheKey(String hashCode, BuildCacheKeyInputs inputs) {
            this.hashCode = hashCode;
            this.inputs = inputs;
        }


        @Override
        public String getHashCode() {
            return hashCode;
        }

        @Override
        public BuildCacheKeyInputs getInputs() {
            return inputs;
        }
    }
}
