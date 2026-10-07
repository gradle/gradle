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

import com.google.common.collect.Interner;
import org.gradle.cache.CacheDecorator;
import org.gradle.cache.IndexedCache;
import org.gradle.cache.IndexedCacheParameters;
import org.gradle.cache.PersistentCache;
import org.gradle.cache.internal.InMemoryCacheDecoratorFactory;
import org.gradle.internal.execution.history.AfterExecutionState;
import org.gradle.internal.execution.history.ExecutionHistoryStore;
import org.gradle.internal.execution.history.PreviousExecutionState;
import org.gradle.internal.fingerprint.FileCollectionFingerprint;
import org.gradle.internal.fingerprint.RootFingerprint;
import org.gradle.internal.fingerprint.RootFingerprintInterner;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.serialize.HashCodeSerializer;

import java.util.Optional;
import java.util.function.Supplier;

public class DefaultExecutionHistoryStore implements ExecutionHistoryStore {

    private final IndexedCache<String, PreviousExecutionState> store;
    private final RootFingerprintForest rootFingerprints;

    public DefaultExecutionHistoryStore(
        Supplier<PersistentCache> cache,
        InMemoryCacheDecoratorFactory inMemoryCacheDecoratorFactory,
        Interner<String> stringInterner,
        RootFingerprintInterner rootFingerprintInterner
    ) {
        CacheDecorator inMemoryCacheDecorator = inMemoryCacheDecoratorFactory.decorator(10000, false);

        IndexedCache<HashCode, RootFingerprint> rootFingerprintCache = cache.get().createIndexedCache(
            IndexedCacheParameters.of("inputFingerprints", new HashCodeSerializer(), new RootFingerprintSerializer(stringInterner))
                .withCacheDecorator(inMemoryCacheDecorator)
        );
        this.rootFingerprints = new DefaultRootFingerprintForest(rootFingerprintCache, rootFingerprintInterner);

        DefaultPreviousExecutionStateSerializer serializer = new DefaultPreviousExecutionStateSerializer(
            new FileCollectionFingerprintSerializer(stringInterner, rootFingerprints),
            new FileSystemSnapshotSerializer(stringInterner),
            new HashCodeSerializer()
        );
        this.store = cache.get().createIndexedCache(
            IndexedCacheParameters.of("executionHistory", String.class, serializer)
                .withCacheDecorator(inMemoryCacheDecorator)
        );
    }

    @Override
    public Optional<PreviousExecutionState> load(String key) {
        PreviousExecutionState previousExecutionState = store.getIfPresent(key);
        if (previousExecutionState == null) {
            return Optional.empty();
        }
        try {
            for (FileCollectionFingerprint fingerprint : previousExecutionState.getInputFileProperties().values()) {
                fingerprint.getRootFingerprints();
            }
        } catch (MissingRootFingerprintException e) {
            // The entry refers to a root fingerprint that is no longer stored, so it is unusable
            store.remove(key);
            return Optional.empty();
        }
        return Optional.of(previousExecutionState);
    }

    @Override
    public void store(String key, AfterExecutionState executionState) {
        DefaultPreviousExecutionState previousExecutionState = DefaultPreviousExecutionState.from(executionState);
        for (FileCollectionFingerprint fingerprint : previousExecutionState.getInputFileProperties().values()) {
            rootFingerprints.store(fingerprint);
        }
        store.put(key, previousExecutionState);
    }

    @Override
    public void remove(String key) {
        store.remove(key);
    }
}
