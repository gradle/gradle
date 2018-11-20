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

package org.gradle.api.internal.artifacts.transform;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedMap;
import org.gradle.caching.internal.origin.OriginMetadata;
import org.gradle.internal.execution.history.AfterPreviousExecutionState;
import org.gradle.internal.execution.history.impl.DefaultAfterPreviousExecutionState;
import org.gradle.internal.execution.history.impl.SerializableFileCollectionFingerprint;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.fingerprint.FileCollectionFingerprint;
import org.gradle.internal.snapshot.ValueSnapshot;
import org.gradle.internal.snapshot.impl.ImplementationSnapshot;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static com.google.common.collect.ImmutableSortedMap.copyOfSorted;
import static com.google.common.collect.Maps.transformValues;

public class TestTransformerExecutionHistoryRepository implements TransformerExecutionHistoryRepository {
    private final Map<String, AfterPreviousExecutionState> executionHistory = new HashMap<>();
    private final File transformationsStoreDirectory;

    public TestTransformerExecutionHistoryRepository(File transformationsStoreDirectory) {
        this.transformationsStoreDirectory = transformationsStoreDirectory;
    }

    @Override
    public Optional<AfterPreviousExecutionState> getPreviousExecution(String identity) {
        return Optional.ofNullable(executionHistory.get(identity));
    }

    @Override
    public void persist(String identity, OriginMetadata originMetadata, ImplementationSnapshot implementationSnapshot, ImmutableSortedMap<String, ValueSnapshot> inputSnapshots, ImmutableSortedMap<String, CurrentFileCollectionFingerprint> inputFileFingerprints, ImmutableSortedMap<String, CurrentFileCollectionFingerprint> outputFingerprints, boolean successful) {
        AfterPreviousExecutionState persistedState = new DefaultAfterPreviousExecutionState(
            originMetadata,
            implementationSnapshot,
            ImmutableList.of(),
            inputSnapshots,
            prepareForSerialization(inputFileFingerprints),
            prepareForSerialization(outputFingerprints),
            successful
        );
        executionHistory.put(identity, persistedState);
    }

    @Override
    public boolean hasCachedResult(TransformationIdentity identity) {
        return false;
    }

    private static ImmutableSortedMap<String, FileCollectionFingerprint> prepareForSerialization(ImmutableSortedMap<String, CurrentFileCollectionFingerprint> fingerprints) {
        return copyOfSorted(transformValues(fingerprints, value -> {
            //noinspection ConstantConditions
            return new SerializableFileCollectionFingerprint(value.getFingerprints(), value.getRootHashes());
        }));
    }

    @Override
    public ImmutableList<File> withWorkspace(TransformationIdentity identity, TransformationWorkspaceAction workspaceAction) {
        String identityString = identity.getIdentity();
        return workspaceAction.useWorkspace(identityString, new File(transformationsStoreDirectory, identityString));
    }
}
