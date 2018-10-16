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
import org.gradle.api.NonNullApi;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.hash.Hasher;
import org.gradle.internal.hash.Hashing;
import org.gradle.internal.snapshot.impl.ImplementationSnapshot;
import org.gradle.util.Path;

import javax.annotation.Nullable;
import java.util.Collection;

@NonNullApi
public class DefaultTaskOutputCachingBuildCacheKeyBuilder implements TaskOutputCachingBuildCacheKeyBuilder {

    private final Hasher hasher = Hashing.newHasher();
    private final Path taskPath;
    private ImplementationSnapshot taskImplementation;
    private ImmutableList<ImplementationSnapshot> actionImplementations;
    private final ImmutableSortedMap.Builder<String, HashCode> inputValueHashes = ImmutableSortedMap.naturalOrder();
    private final ImmutableSortedMap.Builder<String, CurrentFileCollectionFingerprint> inputFiles = ImmutableSortedMap.naturalOrder();
    private final ImmutableSortedMap.Builder<String, String> nonCacheableInputProperties = ImmutableSortedMap.naturalOrder();
    private final ImmutableSortedSet.Builder<String> outputPropertyNames = ImmutableSortedSet.naturalOrder();

    public DefaultTaskOutputCachingBuildCacheKeyBuilder(Path taskPath) {
        this.taskPath = taskPath;
    }

    @Override
    public void appendTaskImplementation(ImplementationSnapshot taskImplementation) {
        this.taskImplementation = taskImplementation;
        taskImplementation.appendToHasher(hasher);
    }

    @Override
    public void appendTaskActionImplementations(Collection<ImplementationSnapshot> taskActionImplementations) {
        ImmutableList.Builder<ImplementationSnapshot> builder = ImmutableList.builder();
        for (ImplementationSnapshot actionImpl : taskActionImplementations) {
            builder.add(actionImpl);
            actionImpl.appendToHasher(hasher);
        }

        this.actionImplementations = builder.build();
    }

    @Override
    public void appendInputValuePropertyHash(String propertyName, HashCode hashCode) {
        hasher.putString(propertyName);
        hasher.putHash(hashCode);
        inputValueHashes.put(propertyName, hashCode);
    }

    @Override
    public void appendInputFilesProperty(String propertyName, CurrentFileCollectionFingerprint fileCollectionFingerprint) {
        hasher.putString(propertyName);
        hasher.putHash(fileCollectionFingerprint.getHash());
        inputFiles.put(propertyName, fileCollectionFingerprint);
    }

    @Override
    public void inputPropertyNotCacheable(String propertyName, String nonCacheableReason) {
        hasher.markAsInvalid(nonCacheableReason);
        nonCacheableInputProperties.put(propertyName, nonCacheableReason);
    }

    @Override
    public void appendOutputPropertyName(String propertyName) {
        outputPropertyNames.add(propertyName);
        hasher.putString(propertyName);
    }

    @Override
    public TaskOutputCachingBuildCacheKey build() {
        BuildCacheKeyInputs inputs = new BuildCacheKeyInputs(taskImplementation, actionImplementations, inputValueHashes.build(), inputFiles.build(), nonCacheableInputProperties.build(), outputPropertyNames.build());
        HashCode hash;
        if (!hasher.isValid()) {
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
        public byte[] getHashCodeBytes() {
            return hashCode == null ? null : hashCode.toByteArray();
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
