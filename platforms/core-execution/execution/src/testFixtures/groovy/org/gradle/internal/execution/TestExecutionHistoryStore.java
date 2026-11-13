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
import java.util.UUID;

import static com.google.common.collect.ImmutableSortedMap.copyOfSorted;
import static com.google.common.collect.Maps.transformValues;

public class TestExecutionHistoryStore implements ExecutionHistoryStore {

    private final Map<String, PreviousExecutionState> executionHistory = new HashMap<>();

    @Override
    public Optional<PreviousExecutionState> load(String key) {
        return Optional.ofNullable(executionHistory.get(key));
    }

    @Override
    public void store(String key, AfterExecutionState executionState) {
        executionHistory.put(key, toPreviousExecutionState(executionState));
    }

    @Override
    public boolean storeIfUnchanged(String key, Optional<PreviousExecutionState> expectedState, AfterExecutionState executionState) {
        Optional<PreviousExecutionState> currentState = load(key);
        if (!sameHistoryEntry(currentState, expectedState)) {
            return false;
        }
        executionHistory.put(key, toPreviousExecutionState(executionState));
        return true;
    }

    @Override
    public void remove(String key) {
        executionHistory.remove(key);
    }

    private static boolean sameHistoryEntry(Optional<PreviousExecutionState> currentState, Optional<PreviousExecutionState> expectedState) {
        if (!currentState.isPresent() || !expectedState.isPresent()) {
            return !currentState.isPresent() && !expectedState.isPresent();
        }
        PreviousExecutionState current = currentState.get();
        PreviousExecutionState expected = expectedState.get();
        if (!(current instanceof DefaultPreviousExecutionState) || !(expected instanceof DefaultPreviousExecutionState)) {
            return false;
        }
        return ((DefaultPreviousExecutionState) current).getExecutionHistoryEntryId()
            .equals(((DefaultPreviousExecutionState) expected).getExecutionHistoryEntryId());
    }

    private static PreviousExecutionState toPreviousExecutionState(AfterExecutionState executionState) {
        return new DefaultPreviousExecutionState(
            UUID.randomUUID().toString(),
            executionState.getOriginMetadata(),
            executionState.getCacheKey(),
            executionState.getImplementation(),
            executionState.getAdditionalImplementations(),
            executionState.getInputProperties(),
            prepareForSerialization(executionState.getInputFileProperties()),
            executionState.getOutputFilesProducedByWork(),
            executionState.isSuccessful()
        );
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
