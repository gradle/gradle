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

package org.gradle.api.internal.changedetection.state;

import org.gradle.cache.CacheBuilder;
import org.gradle.cache.CacheRepository;
import org.gradle.cache.FileLockManager;
import org.gradle.cache.PersistentCache;
import org.gradle.cache.PersistentIndexedCache;
import org.gradle.cache.PersistentIndexedCacheParameters;
import org.gradle.internal.serialize.Serializer;

import javax.annotation.Nullable;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;

import static org.gradle.cache.internal.filelock.LockOptionsBuilder.mode;

public class CrossBuildFileHashCache implements Closeable, TaskHistoryStore {
    private final PersistentCache cache;
    private final InMemoryCacheDecoratorFactory inMemoryCacheDecoratorFactory;

    public CrossBuildFileHashCache(@Nullable File cacheDir, CacheRepository repository, InMemoryCacheDecoratorFactory inMemoryCacheDecoratorFactory) {
        this.inMemoryCacheDecoratorFactory = inMemoryCacheDecoratorFactory;
        CacheBuilder cacheBuilder = cacheDir != null ? repository.cache(cacheDir) : repository.cache("fileHashes");
        cache = cacheBuilder
            .withDisplayName("file hash cache")
            .withLockOptions(mode(FileLockManager.LockMode.None)) // Lock on demand
            .open();
    }

    @Override
    public <K, V> PersistentIndexedCache<K, V> createCache(String cacheName, Class<K> keyType, Serializer<V> valueSerializer, int maxEntriesToKeepInMemory, boolean cacheInMemoryForShortLivedProcesses) {
        PersistentIndexedCacheParameters<K, V> parameters = new PersistentIndexedCacheParameters<K, V>(cacheName, keyType, valueSerializer)
                .cacheDecorator(inMemoryCacheDecoratorFactory.decorator(maxEntriesToKeepInMemory, cacheInMemoryForShortLivedProcesses));
        return cache.createCache(parameters);
    }

    @Override
    public void close() throws IOException {
        cache.close();
    }
}
