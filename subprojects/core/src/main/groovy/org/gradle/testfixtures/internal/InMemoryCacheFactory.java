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
import org.gradle.api.internal.changedetection.InMemoryIndexedCache;
import org.gradle.cache.*;

import java.io.File;
import java.util.Map;

public class InMemoryCacheFactory implements CacheFactory {
    public PersistentCache open(final File cacheDir, CacheUsage usage, Map<String, ?> properties) {
        cacheDir.mkdirs();
        return new InMemoryCache(cacheDir);
    }

    public <K, V> PersistentIndexedCache<K, V> openIndexedCache(File cacheDir, CacheUsage usage, Map<String, ?> properties, Serializer<V> serializer) {
        cacheDir.mkdirs();
        return new InMemoryIndexedCache<K, V>();
    }

    public <E> PersistentStateCache<E> openStateCache(File cacheDir, CacheUsage usage, Map<String, ?> properties, Serializer<E> serializer) {
        cacheDir.mkdirs();
        return new SimpleStateCache<E>(new InMemoryCache(cacheDir), new DefaultSerializer<E>());
    }

    private static class InMemoryCache implements PersistentCache {
        private final File cacheDir;

        public InMemoryCache(File cacheDir) {
            this.cacheDir = cacheDir;
        }

        public File getBaseDir() {
            return cacheDir;
        }

        public boolean isValid() {
            return false;
        }

        public void markValid() {
        }
    }
}
