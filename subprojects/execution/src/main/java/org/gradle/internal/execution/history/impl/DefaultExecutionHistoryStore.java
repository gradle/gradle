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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedMap;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.cache.CacheDecorator;
import org.gradle.cache.PersistentCache;
import org.gradle.cache.PersistentIndexedCache;
import org.gradle.cache.PersistentIndexedCacheParameters;
import org.gradle.cache.internal.InMemoryCacheDecoratorFactory;
import org.gradle.caching.internal.origin.OriginMetadata;
import org.gradle.internal.execution.history.AfterPreviousExecutionState;
import org.gradle.internal.execution.history.ExecutionHistoryStore;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.fingerprint.FileCollectionFingerprint;
import org.gradle.internal.snapshot.ValueSnapshot;
import org.gradle.internal.snapshot.impl.ImplementationSnapshot;

import java.util.Optional;
import java.util.function.Supplier;

import static com.google.common.collect.ImmutableSortedMap.copyOfSorted;
import static com.google.common.collect.Maps.transformValues;

public class DefaultExecutionHistoryStore implements ExecutionHistoryStore {

    private final PersistentIndexedCache<String, AfterPreviousExecutionState> store;

    public DefaultExecutionHistoryStore(
        Supplier<PersistentCache> cache,
        InMemoryCacheDecoratorFactory inMemoryCacheDecoratorFactory,
        StringInterner stringInterner
    ) {
        DefaultPreviousExecutionStateSerializer serializer = new DefaultPreviousExecutionStateSerializer(
            new FileCollectionFingerprintSerializer(stringInterner));

        CacheDecorator inMemoryCacheDecorator = inMemoryCacheDecoratorFactory.decorator(10000, false);
        this.store = cache.get().createCache(
            PersistentIndexedCacheParameters.of("executionHistory", String.class, serializer)
            .withCacheDecorator(inMemoryCacheDecorator)
        );
    }

    @Override
    public Optional<AfterPreviousExecutionState> load(String key) {
        return Optional.ofNullable(store.get(key));
    }

    @Override
    public void store(
        String key,
        OriginMetadata originMetadata,
        ImplementationSnapshot implementation,
        ImmutableList<ImplementationSnapshot> additionalImplementations,
        ImmutableSortedMap<String, ValueSnapshot> inputProperties,
        ImmutableSortedMap<String, CurrentFileCollectionFingerprint> inputFileProperties,
        ImmutableSortedMap<String, CurrentFileCollectionFingerprint> outputFileProperties,
        boolean successful
    ) {
        store.put(key, new DefaultAfterPreviousExecutionState(
            originMetadata,
            implementation,
            additionalImplementations,
            inputProperties,
            prepareForSerialization(inputFileProperties),
            prepareForSerialization(outputFileProperties),
            successful
        ));
    }

    @Override
    public void remove(String key) {
        store.remove(key);
    }

    private static ImmutableSortedMap<String, FileCollectionFingerprint> prepareForSerialization(ImmutableSortedMap<String, CurrentFileCollectionFingerprint> fingerprints) {
        return copyOfSorted(transformValues(fingerprints, value -> {
            //noinspection ConstantConditions
            return new SerializableFileCollectionFingerprint(value.getFingerprints(), value.getRootHashes());
        }));
    }
}
