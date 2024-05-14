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

import org.gradle.cache.CacheCleanupStrategy;
import org.gradle.cache.CacheOpenException;
import org.gradle.cache.CleanupAction;
import org.gradle.cache.CleanupProgressMonitor;
import org.gradle.cache.IndexedCache;
import org.gradle.cache.IndexedCacheParameters;
import org.gradle.cache.LockOptions;
import org.gradle.cache.PersistentCache;
import org.gradle.cache.internal.CacheFactory;
import org.gradle.cache.internal.CacheVisitor;
import org.gradle.internal.Cast;
import org.gradle.internal.Pair;
import org.gradle.internal.serialize.Serializer;
import org.gradle.util.internal.GFileUtils;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class TestInMemoryCacheFactory implements CacheFactory {
    /*
     * In case multiple threads is accessing the cache, for example when running JUnit 5 tests in parallel,
     * the map must be protected from concurrent modification.
     */
    final Map<Pair<File, String>, IndexedCache<?, ?>> caches = Collections.synchronizedMap(new LinkedHashMap<>());

    @Override
    public PersistentCache open(File cacheDir, String displayName, Map<String, ?> properties, LockOptions lockOptions, @Nullable Consumer<? super PersistentCache> initializer, @Nullable CacheCleanupStrategy cacheCleanupStrategy) throws CacheOpenException {
        GFileUtils.mkdirs(cacheDir);
        InMemoryCache cache = new InMemoryCache(cacheDir, displayName, cacheCleanupStrategy != null ? cacheCleanupStrategy.getCleanupAction() : null);
        if (initializer != null) {
            initializer.accept(cache);
        }
        return cache;
    }

    public PersistentCache open(File cacheDir, String displayName) {
        return new InMemoryCache(cacheDir, displayName, CleanupAction.NO_OP);
    }

    @Override
    public void visitCaches(CacheVisitor visitor) {
        throw new UnsupportedOperationException();
    }

    private class InMemoryCache implements PersistentCache {
        private final File cacheDir;
        private final String displayName;
        private boolean closed;
        private final CleanupAction cleanup;

        public InMemoryCache(File cacheDir, String displayName, @Nullable CleanupAction cleanup) {
            this.cacheDir = cacheDir;
            this.displayName = displayName;
            this.cleanup = cleanup;
        }

        @Override
        public void close() {
            cleanup();
            closed = true;
        }

        @Override
        public void cleanup() {
            if (cleanup!=null) {
                synchronized (this) {
                    cleanup.clean(this, CleanupProgressMonitor.NO_OP);
                }
            }
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
        public <K, V> IndexedCache<K, V> createIndexedCache(String name, Class<K> keyType, Serializer<V> valueSerializer) {
            assertNotClosed();
            return createIndexedCache(name, valueSerializer);
        }

        @Override
        public <K, V> boolean indexedCacheExists(IndexedCacheParameters<K, V> parameters) {
            return true;
        }

        @Override
        public <K, V> IndexedCache<K, V> createIndexedCache(IndexedCacheParameters<K, V> parameters) {
            assertNotClosed();
            return createIndexedCache(parameters.getCacheName(), parameters.getValueSerializer());
        }

        private <K, V> IndexedCache<K, V> createIndexedCache(String name, Serializer<V> valueSerializer) {
            assertNotClosed();
            IndexedCache<?, ?> indexedCache = caches.get(Pair.of(cacheDir, name));
            if (indexedCache == null) {
                indexedCache = new TestInMemoryIndexedCache<K, V>(valueSerializer);
                caches.put(Pair.of(cacheDir, name), indexedCache);
            }
            return Cast.uncheckedCast(indexedCache);
        }

        @Override
        public <T> T withFileLock(Supplier<? extends T> action) {
            return action.get();
        }

        @Override
        public void withFileLock(Runnable action) {
            action.run();
        }

        @Override
        public <T> T useCache(Supplier<? extends T> action) {
            assertNotClosed();
            // The contract of useCache() means we have to provide some basic synchronization.
            synchronized (this) {
                return action.get();
            }
        }

        @Override
        public void useCache(Runnable action) {
            assertNotClosed();
            // The contract of useCache() means we have to provide some basic synchronization.
            synchronized (this) {
                action.run();
            }
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
