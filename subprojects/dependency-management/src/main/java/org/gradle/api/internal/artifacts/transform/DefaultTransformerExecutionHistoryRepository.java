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
import org.gradle.internal.execution.history.ExecutionHistoryStore;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.snapshot.impl.ImplementationSnapshot;

import java.io.File;
import java.util.Optional;

public class DefaultTransformerExecutionHistoryRepository implements TransformerExecutionHistoryRepository {
    
    private final ExecutionHistoryStore executionHistoryStore;
    private final TransformerWorkspaceProvider workspaceProvider;

    public DefaultTransformerExecutionHistoryRepository(TransformerWorkspaceProvider workspaceProvider, ExecutionHistoryStore executionHistoryStore) {
        this.workspaceProvider = workspaceProvider;
        this.executionHistoryStore = executionHistoryStore;
    }

    @Override
    public Optional<AfterPreviousExecutionState> getPreviousExecution(HashCode cacheKey) {
        return Optional.ofNullable(executionHistoryStore.load(cacheKey.toString()));
    }

    @Override
    public void persist(HashCode cacheKey, OriginMetadata originMetadata, ImplementationSnapshot implementationSnapshot, ImmutableSortedMap<String, CurrentFileCollectionFingerprint> outputFingerprints, boolean successful) {
        executionHistoryStore.store(cacheKey.toString(), originMetadata, implementationSnapshot, ImmutableList.of(), ImmutableSortedMap.of(), ImmutableSortedMap.of(), outputFingerprints, successful);
    }

    @Override
    public File getWorkspace(File toBeTransformed, HashCode cacheKey) {
        return workspaceProvider.getWorkspace(toBeTransformed, cacheKey);
    }
}
