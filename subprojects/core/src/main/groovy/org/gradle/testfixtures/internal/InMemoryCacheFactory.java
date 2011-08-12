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

import org.gradle.CacheUsage;
import org.gradle.api.Action;
import org.gradle.api.internal.changedetection.InMemoryIndexedCache;
import org.gradle.cache.*;
import org.gradle.cache.internal.*;
import org.gradle.util.UncheckedException;

import java.io.File;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.Callable;

public class InMemoryCacheFactory implements CacheFactory {
    public PersistentCache openStore(File storeDir, FileLockManager.LockMode lockMode, CrossVersionMode crossVersionMode, Action<? super PersistentCache> initializer) throws CacheOpenException {
        return open(storeDir, CacheUsage.ON, Collections.<String, Object>emptyMap(), lockMode, crossVersionMode, initializer);
    }

    public PersistentCache open(File cacheDir, CacheUsage usage, Map<String, ?> properties, FileLockManager.LockMode lockMode, CrossVersionMode crossVersionMode, Action<? super PersistentCache> initializer) {
        cacheDir.mkdirs();
        InMemoryCache cache = new InMemoryCache(cacheDir);
        if (initializer != null) {
            initializer.execute(cache);
        }
        return cache;
    }

    public <K, V> PersistentIndexedCache<K, V> openIndexedCache(File cacheDir, CacheUsage usage, Map<String, ?> properties, FileLockManager.LockMode lockMode, CrossVersionMode crossVersionMode, Serializer<V> serializer) {
        return new InMemoryIndexedCache<K, V>();
    }

    public <E> PersistentStateCache<E> openStateCache(File cacheDir, CacheUsage usage, Map<String, ?> properties, FileLockManager.LockMode lockMode, CrossVersionMode crossVersionMode, Serializer<E> serializer) {
        cacheDir.mkdirs();
        return new SimpleStateCache<E>(new File(cacheDir, "state.bin"), new NoOpFileLock(), new DefaultSerializer<E>());
    }

    private static class NoOpFileLock implements FileLock {
        public boolean getUnlockedCleanly() {
            return true;
        }

        public boolean isLockFile(File file) {
            return false;
        }

        public <T> T readFromFile(Callable<T> action) throws LockTimeoutException {
            try {
                return action.call();
            } catch (Exception e) {
                throw UncheckedException.asUncheckedException(e);
            }
        }

        public void writeToFile(Runnable action) throws LockTimeoutException {
            action.run();
        }

        public void close() {
        }
    }

    private static class InMemoryCache implements PersistentCache {
        private final File cacheDir;

        public InMemoryCache(File cacheDir) {
            this.cacheDir = cacheDir;
        }

        public File getBaseDir() {
            return cacheDir;
        }
    }
}
