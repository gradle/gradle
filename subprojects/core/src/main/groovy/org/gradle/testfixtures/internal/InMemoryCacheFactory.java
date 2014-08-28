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
package org.gradle.testfixtures.internal;

import com.google.common.collect.Maps;
import org.gradle.api.Action;
import org.gradle.cache.*;
import org.gradle.cache.internal.CacheFactory;
import org.gradle.cache.internal.filelock.LockOptions;
import org.gradle.internal.Factory;
import org.gradle.messaging.serialize.Serializer;
import org.gradle.util.GFileUtils;

import java.io.File;
import java.util.Collections;
import java.util.Map;

public class InMemoryCacheFactory implements CacheFactory {
    public PersistentCache openStore(File storeDir, String displayName, LockOptions lockOptions, Action<? super PersistentCache> initializer) throws CacheOpenException {
        return open(storeDir, displayName, null, Collections.<String, Object>emptyMap(), lockOptions, initializer);
    }

    public PersistentCache open(File cacheDir, String displayName, CacheValidator cacheValidator, Map<String, ?> properties, LockOptions lockOptions, Action<? super PersistentCache> initializer) {
        GFileUtils.mkdirs(cacheDir);
        InMemoryCache cache = new InMemoryCache(cacheDir);
        if (initializer != null) {
            initializer.execute(cache);
        }
        return cache;
    }

    public <K, V> PersistentIndexedCache<K, V> openIndexedCache(File cacheDir, CacheValidator validator, Map<String, ?> properties, LockOptions lockOptions, Serializer<V> serializer) {
        return new InMemoryIndexedCache<K, V>(serializer);
    }

    public static class InMemoryCache implements PersistentCache {

        final Map<String, PersistentIndexedCache<?, ?>> caches = Maps.newLinkedHashMap();

        private final File cacheDir;
        private boolean closed;

        public InMemoryCache(File cacheDir) {
            this.cacheDir = cacheDir;
        }

        public void close() {
            closed = true;
        }

        public boolean isClosed() {
            return closed;
        }

        public File getBaseDir() {
            return cacheDir;
        }

        private void assertNotClosed() {
            if (closed) {
                throw new IllegalStateException("cache is closed");
            }
        }

        public <K, V> PersistentIndexedCache<K, V> createCache(String name, Class<K> keyType, Serializer<V> valueSerializer) {
            assertNotClosed();
            return createCache(name, valueSerializer);
        }

        public <K, V> PersistentIndexedCache<K, V> createCache(PersistentIndexedCacheParameters<K, V> parameters) {
            assertNotClosed();
            return createCache(parameters.getCacheName(), parameters.getValueSerializer());
        }

        private <K, V> PersistentIndexedCache<K, V> createCache(String name, Serializer<V> valueSerializer) {
            assertNotClosed();
            InMemoryIndexedCache<K, V> cache = new InMemoryIndexedCache<K, V>(valueSerializer);
            caches.put(name, cache);
            return cache;
        }

        public <T> T useCache(String operationDisplayName, Factory<? extends T> action) {
            assertNotClosed();
            // The contract of useCache() means we have to provide some basic synchronization.
            synchronized (this) {
                return action.create();
            }
        }

        public void useCache(String operationDisplayName, Runnable action) {
            assertNotClosed();
            action.run();
        }

        public <T> T longRunningOperation(String operationDisplayName, Factory<? extends T> action) {
            assertNotClosed();
            return action.create();
        }

        public void longRunningOperation(String operationDisplayName, Runnable action) {
            assertNotClosed();
            action.run();
        }
    }
}
