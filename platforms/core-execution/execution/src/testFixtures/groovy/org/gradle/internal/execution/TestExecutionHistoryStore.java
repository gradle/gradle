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

import com.google.common.collect.ImmutableSortedMap;
import org.gradle.internal.execution.history.AfterExecutionState;
import org.gradle.internal.execution.history.ExecutionHistoryStore;
import org.gradle.internal.execution.history.PreviousExecutionState;
import org.gradle.internal.execution.history.impl.DefaultPreviousExecutionState;
import org.gradle.internal.execution.history.impl.SerializableFileCollectionFingerprint;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.fingerprint.FileCollectionFingerprint;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static com.google.common.collect.ImmutableSortedMap.copyOfSorted;
import static com.google.common.collect.Maps.transformValues;

public class TestExecutionHistoryStore implements ExecutionHistoryStore {

    private final Map<String, PreviousExecutionState> executionHistory = new HashMap<>();

    @Override
    public Optional<PreviousExecutionState> load(String key) {
        return Optional.ofNullable(executionHistory.get(key));
    }

    @Override
    public void store(String key, boolean successful, AfterExecutionState executionState) {
        executionHistory.put(key, new DefaultPreviousExecutionState(
            executionState.getOriginMetadata(),
            executionState.getImplementation(),
            executionState.getAdditionalImplementations(),
            executionState.getInputProperties(),
            prepareForSerialization(executionState.getInputFileProperties()),
            executionState.getOutputFilesProducedByWork(),
            successful
        ));
    }

    @Override
    public void remove(String key) {
        executionHistory.remove(key);
    }

    private static ImmutableSortedMap<String, FileCollectionFingerprint> prepareForSerialization(ImmutableSortedMap<String, CurrentFileCollectionFingerprint> fingerprints) {
        return copyOfSorted(transformValues(
            fingerprints,
            value -> value.archive(SerializableFileCollectionFingerprint::new)
        ));
    }

    public Map<String, PreviousExecutionState> getExecutionHistory() {
        return executionHistory;
    }
}
