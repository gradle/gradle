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
import org.gradle.cache.CacheBuilder;
import org.gradle.cache.CacheOpenException;
import org.gradle.cache.CleanupAction;
import org.gradle.cache.CleanupProgressMonitor;
import org.gradle.cache.LockOptions;
import org.gradle.cache.PersistentCache;
import org.gradle.cache.PersistentIndexedCache;
import org.gradle.cache.PersistentIndexedCacheParameters;
import org.gradle.cache.internal.CacheFactory;
import org.gradle.internal.Cast;
import org.gradle.internal.Factory;
import org.gradle.internal.Pair;
import org.gradle.internal.serialize.Serializer;
import org.gradle.util.GFileUtils;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

public class InMemoryCacheFactory implements CacheFactory {
    final Map<Pair<File, String>, PersistentIndexedCache<?, ?>> caches = Maps.newLinkedHashMap();

    @Override
    public PersistentCache open(File cacheDir, String displayName, Map<String, ?> properties, CacheBuilder.LockTarget lockTarget, LockOptions lockOptions, Action<? super PersistentCache> initializer, CleanupAction cleanup) throws CacheOpenException {
        GFileUtils.mkdirs(cacheDir);
        InMemoryCache cache = new InMemoryCache(cacheDir, displayName, cleanup);
        if (initializer != null) {
            initializer.execute(cache);
        }
        return cache;
    }

    public PersistentCache open(File cacheDir, String displayName) {
        return new InMemoryCache(cacheDir, displayName, CleanupAction.NO_OP);
    }

    private class InMemoryCache implements PersistentCache {
        private final File cacheDir;
        private final String displayName;
        private boolean closed;
        private final CleanupAction cleanup;

        public InMemoryCache(File cacheDir, String displayName, CleanupAction cleanup) {
            this.cacheDir = cacheDir;
            this.displayName = displayName;
            this.cleanup = cleanup;
        }

        @Override
        public void close() {
            if (cleanup!=null) {
                synchronized (this) {
                    cleanup.clean(this, CleanupProgressMonitor.NO_OP);
                }
            }
            closed = true;
        }

        public boolean isClosed() {
            return closed;
        }

        @Override
        public File getBaseDir() {
            return cacheDir;
        }

        @Override
        public Collection<File> getReservedCacheFiles() {
            return Collections.emptyList();
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
        public <K, V> boolean cacheExists(PersistentIndexedCacheParameters<K, V> parameters) {
            return true;
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
        public <T> T withFileLock(Factory<? extends T> action) {
            return action.create();
        }

        @Override
        public void withFileLock(Runnable action) {
            action.run();
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
        public String getDisplayName() {
            return "InMemoryCache '" + displayName + "' " + cacheDir;
        }

        @Override
        public String toString() {
            return getDisplayName();
        }
    }
}
