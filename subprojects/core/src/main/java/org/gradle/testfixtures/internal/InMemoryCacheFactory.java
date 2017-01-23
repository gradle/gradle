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
import org.gradle.api.Nullable;
import org.gradle.cache.CacheBuilder;
import org.gradle.cache.CacheOpenException;
import org.gradle.cache.CacheValidator;
import org.gradle.cache.PersistentCache;
import org.gradle.cache.PersistentIndexedCache;
import org.gradle.cache.PersistentIndexedCacheParameters;
import org.gradle.cache.internal.CacheFactory;
import org.gradle.cache.internal.filelock.LockOptions;
import org.gradle.internal.Cast;
import org.gradle.internal.Factory;
import org.gradle.internal.Pair;
import org.gradle.internal.serialize.Serializer;
import org.gradle.util.GFileUtils;

import java.io.File;
import java.util.Map;

public class InMemoryCacheFactory implements CacheFactory {
    final Map<Pair<File, String>, PersistentIndexedCache<?, ?>> caches = Maps.newLinkedHashMap();

    @Override
    public PersistentCache open(File cacheDir, String displayName, @Nullable CacheValidator cacheValidator, Map<String, ?> properties, CacheBuilder.LockTarget lockTarget, LockOptions lockOptions, Action<? super PersistentCache> initializer) throws CacheOpenException {
        GFileUtils.mkdirs(cacheDir);
        InMemoryCache cache = new InMemoryCache(cacheDir);
        if (initializer != null) {
            initializer.execute(cache);
        }
        return cache;
    }

    public PersistentCache open(File cacheDir, String displayName) {
        return new InMemoryCache(cacheDir);
    }

    private class InMemoryCache implements PersistentCache {
        private final File cacheDir;
        private boolean closed;

        public InMemoryCache(File cacheDir) {
            this.cacheDir = cacheDir;
        }

        @Override
        public void close() {
            closed = true;
        }

        public boolean isClosed() {
            return closed;
        }

        @Override
        public File getBaseDir() {
            return cacheDir;
        }

        private void assertNotClosed() {
            if (closed) {
                throw new IllegalStateException("cache is closed");
            }
        }

        @Override
        public <K, V> PersistentIndexedCache<K, V> createCache(String name, Class<K> keyType, Serializer<V> valueSerializer) {
            assertNotClosed();
            return createCache(name, valueSerializer);
        }

        @Override
        public void flush() {

        }

        @Override
        public <K, V> PersistentIndexedCache<K, V> createCache(PersistentIndexedCacheParameters<K, V> parameters) {
            assertNotClosed();
            return createCache(parameters.getCacheName(), parameters.getValueSerializer());
        }

        private <K, V> PersistentIndexedCache<K, V> createCache(String name, Serializer<V> valueSerializer) {
            assertNotClosed();
            PersistentIndexedCache<?, ?> indexedCache = caches.get(Pair.of(cacheDir, name));
            if (indexedCache == null) {
                indexedCache = new InMemoryIndexedCache<K, V>(valueSerializer);
                caches.put(Pair.of(cacheDir, name), indexedCache);
            }
            return Cast.uncheckedCast(indexedCache);
        }

        @Override
        public <T> T useCache(Factory<? extends T> action) {
            assertNotClosed();
            // The contract of useCache() means we have to provide some basic synchronization.
            synchronized (this) {
                return action.create();
            }
        }

        @Override
        public void useCache(Runnable action) {
            assertNotClosed();
            action.run();
        }

        @Override
        public <T> T longRunningOperation(Factory<? extends T> action) {
            assertNotClosed();
            return action.create();
        }

        @Override
        public void longRunningOperation(Runnable action) {
            assertNotClosed();
            action.run();
        }
    }
}
