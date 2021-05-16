/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.internal.execution;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedMap;
import org.gradle.caching.BuildCacheKey;
import org.gradle.caching.internal.origin.OriginMetadata;
import org.gradle.internal.execution.history.AfterExecutionState;
import org.gradle.internal.execution.history.ExecutionHistoryStore;
import org.gradle.internal.execution.history.impl.DefaultAfterExecutionState;
import org.gradle.internal.execution.history.impl.SerializableFileCollectionFingerprint;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.fingerprint.FileCollectionFingerprint;
import org.gradle.internal.snapshot.FileSystemSnapshot;
import org.gradle.internal.snapshot.ValueSnapshot;
import org.gradle.internal.snapshot.impl.ImplementationSnapshot;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static com.google.common.collect.ImmutableSortedMap.copyOfSorted;
import static com.google.common.collect.Maps.transformValues;

public class TestExecutionHistoryStore implements ExecutionHistoryStore {

    private final Map<String, AfterExecutionState> executionHistory = new HashMap<>();
    private final Map<String, AfterExecutionState> successHistory = new HashMap<>();

    @Override
    public Optional<AfterExecutionState> loadLastState(String key) {
        return Optional.ofNullable(executionHistory.get(key));
    }

    @Override
    public Optional<AfterExecutionState> loadLastSuccessfulState(String key) {
        return Optional.ofNullable(successHistory.get(key));
    }

    @Override
    public void store(
        boolean success,
        String key,
        OriginMetadata originMetadata,
        @Nullable BuildCacheKey cacheKey,
        ImplementationSnapshot implementation,
        ImmutableList<ImplementationSnapshot> additionalImplementations,
        ImmutableSortedMap<String, ValueSnapshot> inputProperties,
        ImmutableSortedMap<String, CurrentFileCollectionFingerprint> inputFileProperties,
        ImmutableSortedMap<String, FileSystemSnapshot> outputFileProperties
    ) {
        DefaultAfterExecutionState state = new DefaultAfterExecutionState(
            originMetadata,
            cacheKey,
            implementation,
            additionalImplementations,
            inputProperties,
            prepareForSerialization(inputFileProperties),
            outputFileProperties
        );
        executionHistory.put(key, state);
        if (success) {
            successHistory.put(key, state);
        }
    }

    @Override
    public void remove(String key) {
        executionHistory.remove(key);
        successHistory.remove(key);
    }

    private static ImmutableSortedMap<String, FileCollectionFingerprint> prepareForSerialization(ImmutableSortedMap<String, CurrentFileCollectionFingerprint> fingerprints) {
        return copyOfSorted(transformValues(fingerprints, value -> {
            //noinspection ConstantConditions
            return new SerializableFileCollectionFingerprint(value.getFingerprints(), value.getRootHashes());
        }));
    }

    public Map<String, AfterExecutionState> getExecutionHistory() {
        return executionHistory;
    }
}
