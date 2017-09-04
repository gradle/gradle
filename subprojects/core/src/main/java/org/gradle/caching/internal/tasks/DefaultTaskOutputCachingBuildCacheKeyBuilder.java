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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Lists;
import org.gradle.api.internal.changedetection.state.ImplementationSnapshot;
import org.gradle.caching.internal.BuildCacheHasher;
import org.gradle.caching.internal.DefaultBuildCacheHasher;
import org.gradle.internal.hash.HashCode;
import org.gradle.util.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class DefaultTaskOutputCachingBuildCacheKeyBuilder implements TaskOutputCachingBuildCacheKeyBuilder {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultTaskOutputCachingBuildCacheKeyBuilder.class);

    private final BuildCacheHasher hasher = new DefaultBuildCacheHasher();
    private final Path taskPath;
    private String taskClass;
    private HashCode classLoaderHash;
    private List<HashCode> actionClassLoaderHashes;
    private ImmutableList<String> actionTypes;
    private final ImmutableSortedMap.Builder<String, HashCode> inputHashes = ImmutableSortedMap.naturalOrder();
    private final ImmutableSortedSet.Builder<String> outputPropertyNames = ImmutableSortedSet.naturalOrder();

    public DefaultTaskOutputCachingBuildCacheKeyBuilder(Path taskPath) {
        this.taskPath = taskPath;
    }

    @Override
    public void appendTaskImplementation(ImplementationSnapshot taskImplementation) {
        this.taskClass = taskImplementation.getTypeName();
        hasher.putString(taskClass);
        log("taskClass", taskClass);

        if (!taskImplementation.hasUnknownClassLoader()) {
            HashCode hashCode = taskImplementation.getClassLoaderHash();
            this.classLoaderHash = hashCode;
            hasher.putHash(hashCode);
            log("classLoaderHash", hashCode);
        }
    }

    @Override
    public void appendTaskActionImplementations(Collection<ImplementationSnapshot> taskActionImplementations) {
        ImmutableList.Builder<String> actionTypes = ImmutableList.builder();
        List<HashCode> actionClassLoaderHashes = Lists.newArrayListWithCapacity(taskActionImplementations.size());
        for (ImplementationSnapshot actionImpl : taskActionImplementations) {
            String actionType = actionImpl.getTypeName();
            actionTypes.add(actionType);
            hasher.putString(actionType);
            log("actionType", actionType);

            HashCode hashCode;
            if (actionImpl.hasUnknownClassLoader()) {
                hashCode = null;
            } else {
                hashCode = actionImpl.getClassLoaderHash();
                hasher.putHash(hashCode);
            }
            actionClassLoaderHashes.add(hashCode);
            log("actionClassLoaderHash", hashCode);
        }

        this.actionTypes = actionTypes.build();
        this.actionClassLoaderHashes = Collections.unmodifiableList(actionClassLoaderHashes);
    }

    @Override
    public void appendInputPropertyHash(String propertyName, HashCode hashCode) {
        hasher.putString(propertyName);
        hasher.putHash(hashCode);
        inputHashes.put(propertyName, hashCode);
        LOGGER.info("Appending inputPropertyHash for '{}' to build cache key: {}", propertyName, hashCode);
    }

    @Override
    public void appendOutputPropertyName(String propertyName) {
        outputPropertyNames.add(propertyName);
        hasher.putString(propertyName);
        log("outputPropertyName", propertyName);
    }

    private static void log(String name, Object value) {
        LOGGER.info("Appending {} to build cache key: {}", name, value);
    }

    @Override
    public TaskOutputCachingBuildCacheKey build() {
        BuildCacheKeyInputs inputs = new BuildCacheKeyInputs(taskClass, classLoaderHash, actionClassLoaderHashes, actionTypes, inputHashes.build(), outputPropertyNames.build());
        HashCode hash;
        if (classLoaderHash == null || actionClassLoaderHashes.contains(null)) {
            hash = null;
        } else {
            hash = hasher.hash();
        }
        return new DefaultTaskOutputCachingBuildCacheKey(taskPath, hash, inputs);
    }

    private static class DefaultTaskOutputCachingBuildCacheKey implements TaskOutputCachingBuildCacheKey {

        private final Path taskPath;
        private final HashCode hashCode;
        private final BuildCacheKeyInputs inputs;

        private DefaultTaskOutputCachingBuildCacheKey(Path taskPath, @Nullable HashCode hashCode, BuildCacheKeyInputs inputs) {
            this.taskPath = taskPath;
            this.hashCode = hashCode;
            this.inputs = inputs;
        }

        @Override
        public Path getTaskPath() {
            return taskPath;
        }

        @Override
        public String getHashCode() {
            return Preconditions.checkNotNull(hashCode, "Cannot determine hash code for invalid build cache key").toString();
        }

        @Override
        public BuildCacheKeyInputs getInputs() {
            return inputs;
        }

        @Override
        public boolean isValid() {
            return hashCode != null;
        }

        @Override
        public String getDisplayName() {
            if (hashCode == null) {
                return "INVALID cache key for task '" + taskPath + "'";
            }
            return hashCode + " for task '" + taskPath + "'";
        }

        @Override
        public String toString() {
            return String.valueOf(hashCode);
        }
    }
}
