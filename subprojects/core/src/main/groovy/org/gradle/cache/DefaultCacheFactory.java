/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.cache;

import org.gradle.CacheUsage;
import org.gradle.util.GFileUtils;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class DefaultCacheFactory implements CacheFactory {
    private final Map<File, CacheReferenceImpl> caches = new HashMap<File, CacheReferenceImpl>();

    public CacheReference<PersistentCache> open(File cacheDir, CacheUsage usage, Map<String, ?> properties) {
        File canonicalDir = GFileUtils.canonicalise(cacheDir);
        CacheReferenceImpl cacheReference = caches.get(canonicalDir);
        if (cacheReference == null) {
            DefaultPersistentDirectoryCache cache = createCache(usage, properties, canonicalDir);
            cacheReference = new CacheReferenceImpl(cache, properties);
            caches.put(canonicalDir, cacheReference);
        } else {
            if (usage != CacheUsage.ON) {
                throw new IllegalStateException(String.format("Cannot rebuild cache '%s' as it is already open.", cacheDir));
            }
            if (!properties.equals(cacheReference.properties)) {
                throw new IllegalStateException(String.format("Cache '%s' is already open with different state.", cacheDir));
            }
        }
        cacheReference.addReference();
        return cacheReference;
    }

    public void close() {
        for (CacheReferenceImpl cacheReference : caches.values()) {
            cacheReference.close();
        }
    }

    private DefaultPersistentDirectoryCache createCache(CacheUsage usage, Map<String, ?> properties, File canonicalDir) {
        return new DefaultPersistentDirectoryCache(canonicalDir, usage, properties);
    }

    private class CacheReferenceImpl implements CacheFactory.CacheReference<PersistentCache> {
        private final DefaultPersistentDirectoryCache cache;
        private final Map<String, ?> properties;
        private int references;

        public CacheReferenceImpl(DefaultPersistentDirectoryCache cache, Map<String, ?> properties) {
            this.cache = cache;
            this.properties = properties;
        }

        public PersistentCache getCache() {
            return cache;
        }

        public void release() {
            references--;
            if (references == 0) {
                caches.values().remove(this);
                close();
            }
        }

        public void addReference() {
            references++;
        }

        public void close() {
            cache.close();
        }
    }
}
