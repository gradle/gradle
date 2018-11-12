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
import org.gradle.internal.Try;
import org.gradle.internal.execution.history.AfterPreviousExecutionState;
import org.gradle.internal.execution.history.ExecutionHistoryStore;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.snapshot.impl.ImplementationSnapshot;

import java.io.File;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

public abstract class DefaultTransformerExecutionHistoryRepository implements TransformerExecutionHistoryRepository {
    
    private final ExecutionHistoryStore executionHistoryStore;
    private final TransformerWorkspaceProvider workspaceProvider;
    private final Map<TransformationIdentity, ImmutableList<File>> inMemoryResultCache = new ConcurrentHashMap<>();

    public DefaultTransformerExecutionHistoryRepository(TransformerWorkspaceProvider workspaceProvider, ExecutionHistoryStore executionHistoryStore) {
        this.workspaceProvider = workspaceProvider;
        this.executionHistoryStore = executionHistoryStore;
    }

    @Override
    public Optional<AfterPreviousExecutionState> getPreviousExecution(String identity) {
        return Optional.ofNullable(executionHistoryStore.load(identity));
    }

    @Override
    public void persist(String identity, OriginMetadata originMetadata, ImplementationSnapshot implementationSnapshot, ImmutableSortedMap<String, CurrentFileCollectionFingerprint> primaryInputFingerprints, ImmutableSortedMap<String, CurrentFileCollectionFingerprint> outputFingerprints, boolean successful) {
        executionHistoryStore.store(identity, originMetadata, implementationSnapshot, ImmutableList.of(), ImmutableSortedMap.of(), primaryInputFingerprints, outputFingerprints, successful);
    }

    @Override
    public boolean hasCachedResult(TransformationIdentity identity) {
        return inMemoryResultCache.containsKey(identity);
    }

    @Override
    public Try<ImmutableList<File>> withWorkspace(TransformationIdentity identity, BiFunction<String, File, Try<ImmutableList<File>>> useWorkspace) {
        ImmutableList<File> resultFromCache = inMemoryResultCache.get(identity);
        if (resultFromCache != null) {
            return Try.successful(resultFromCache);
        }
        return workspaceProvider.withWorkspace(identity, (identityString, workspace) -> {
            ImmutableList<File> fromCache = inMemoryResultCache.get(identity);
            if (fromCache != null) {
                return Try.successful(fromCache);
            }
            Try<ImmutableList<File>> transformationResult = useWorkspace.apply(identityString, workspace);
            transformationResult.ifSuccessful(files -> inMemoryResultCache.put(identity, files));
            return transformationResult;
        });
    }

    public void clearInMemoryCache() {
        inMemoryResultCache.clear();
    }
}
