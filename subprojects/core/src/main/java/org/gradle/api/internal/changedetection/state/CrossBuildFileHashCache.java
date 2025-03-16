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

import org.gradle.cache.FileLockManager;
import org.gradle.cache.IndexedCache;
import org.gradle.cache.IndexedCacheParameters;
import org.gradle.cache.PersistentCache;
import org.gradle.cache.internal.InMemoryCacheDecoratorFactory;
import org.gradle.cache.scopes.ScopedCacheBuilderFactory;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

import java.io.Closeable;

@ServiceScope({Scope.UserHome.class, Scope.BuildSession.class})
public class CrossBuildFileHashCache implements Closeable {

    private final PersistentCache cache;
    private final InMemoryCacheDecoratorFactory inMemoryCacheDecoratorFactory;

    public CrossBuildFileHashCache(ScopedCacheBuilderFactory cacheBuilderFactory, InMemoryCacheDecoratorFactory inMemoryCacheDecoratorFactory, Kind cacheKind) {
        this.inMemoryCacheDecoratorFactory = inMemoryCacheDecoratorFactory;
        cache = cacheBuilderFactory.createCacheBuilder(cacheKind.cacheId)
            .withDisplayName(cacheKind.description)
            .withInitialLockMode(FileLockManager.LockMode.OnDemand)
            .open();
    }

    public <K, V> IndexedCache<K, V> createIndexedCache(IndexedCacheParameters<K, V> parameters, int maxEntriesToKeepInMemory, boolean cacheInMemoryForShortLivedProcesses) {
        return cache.createIndexedCache(parameters
            .withCacheDecorator(inMemoryCacheDecoratorFactory.decorator(maxEntriesToKeepInMemory, cacheInMemoryForShortLivedProcesses))
        );
    }

    @Override
    public void close() {
        cache.close();
    }

    public enum Kind {
        FILE_HASHES("fileHashes", "file hash cache"),
        CHECKSUMS("checksums", "checksums cache");
        private final String cacheId;
        private final String description;

        Kind(String cacheId, String description) {
            this.cacheId = cacheId;
            this.description = description;
        }

        public String getCacheId() {
            return cacheId;
        }

        public String getDescription() {
            return description;
        }
    }
}
