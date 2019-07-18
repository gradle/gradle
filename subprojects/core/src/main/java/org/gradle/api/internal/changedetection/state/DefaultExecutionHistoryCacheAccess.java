/*
 * Copyright 2011 the original author or authors.
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

import org.gradle.cache.CacheRepository;
import org.gradle.cache.FileLockManager;
import org.gradle.cache.PersistentCache;
import org.gradle.cache.PersistentIndexedCache;
import org.gradle.cache.PersistentIndexedCacheParameters;
import org.gradle.cache.internal.InMemoryCacheDecoratorFactory;
import org.gradle.internal.execution.history.ExecutionHistoryCacheAccess;

import javax.annotation.Nullable;
import java.io.Closeable;

import static org.gradle.cache.internal.filelock.LockOptionsBuilder.mode;

public class DefaultExecutionHistoryCacheAccess implements ExecutionHistoryCacheAccess, Closeable {
    private final InMemoryCacheDecoratorFactory inMemoryCacheDecoratorFactory;
    private final PersistentCache cache;

    public DefaultExecutionHistoryCacheAccess(@Nullable Object scope, CacheRepository cacheRepository, InMemoryCacheDecoratorFactory inMemoryCacheDecoratorFactory) {
        this.inMemoryCacheDecoratorFactory = inMemoryCacheDecoratorFactory;
        cache = cacheRepository
                .cache(scope, "executionHistory")
                .withDisplayName("execution history cache")
                .withLockOptions(mode(FileLockManager.LockMode.None)) // Lock on demand
                .open();
    }

    @Override
    public void close() {
        cache.close();
    }

    @Override
    public <K, V> PersistentIndexedCache<K, V> createCache(PersistentIndexedCacheParameters<K, V> parameters, int maxEntriesToKeepInMemory, boolean cacheInMemoryForShortLivedProcesses) {
        return cache.createCache(parameters
            .withCacheDecorator(inMemoryCacheDecoratorFactory.decorator(maxEntriesToKeepInMemory, cacheInMemoryForShortLivedProcesses))
        );
    }

}
