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

package org.gradle.internal.execution.history.impl;

import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Interner;
import org.gradle.cache.CacheDecorator;
import org.gradle.cache.IndexedCacheParameters;
import org.gradle.cache.PersistentCache;
import org.gradle.cache.IndexedCache;
import org.gradle.cache.internal.InMemoryCacheDecoratorFactory;
import org.gradle.internal.execution.history.AfterExecutionState;
import org.gradle.internal.execution.history.ExecutionHistoryStore;
import org.gradle.internal.execution.history.PreviousExecutionState;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.fingerprint.FileCollectionFingerprint;
import org.gradle.internal.hash.ClassLoaderHierarchyHasher;

import java.util.Optional;
import java.util.function.Supplier;

import static com.google.common.collect.ImmutableSortedMap.copyOfSorted;
import static com.google.common.collect.Maps.transformValues;

public class DefaultExecutionHistoryStore implements ExecutionHistoryStore {

    private final IndexedCache<String, PreviousExecutionState> store;

    public DefaultExecutionHistoryStore(
        Supplier<PersistentCache> cache,
        InMemoryCacheDecoratorFactory inMemoryCacheDecoratorFactory,
        Interner<String> stringInterner,
        ClassLoaderHierarchyHasher classLoaderHasher
    ) {
        DefaultPreviousExecutionStateSerializer serializer = new DefaultPreviousExecutionStateSerializer(
            new FileCollectionFingerprintSerializer(stringInterner),
            new FileSystemSnapshotSerializer(stringInterner),
            classLoaderHasher
        );

        CacheDecorator inMemoryCacheDecorator = inMemoryCacheDecoratorFactory.decorator(10000, false);
        this.store = cache.get().createIndexedCache(
            IndexedCacheParameters.of("executionHistory", String.class, serializer)
            .withCacheDecorator(inMemoryCacheDecorator)
        );
    }

    @Override
    public Optional<PreviousExecutionState> load(String key) {
        return Optional.ofNullable(store.getIfPresent(key));
    }

    @Override
    public void store(String key, boolean successful, AfterExecutionState executionState) {
        store.put(key, new DefaultPreviousExecutionState(
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
        store.remove(key);
    }

    private static ImmutableSortedMap<String, FileCollectionFingerprint> prepareForSerialization(ImmutableSortedMap<String, CurrentFileCollectionFingerprint> fingerprints) {
        return copyOfSorted(transformValues(
            fingerprints,
            value -> value.archive(SerializableFileCollectionFingerprint::new)
        ));
    }
}
