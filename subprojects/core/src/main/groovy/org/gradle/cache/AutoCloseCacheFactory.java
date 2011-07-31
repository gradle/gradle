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

public class AutoCloseCacheFactory implements CacheFactory {
    private final CacheFactory cacheFactory;
    private final Map<File, CacheInfo> openCaches = new HashMap<File, CacheInfo>();

    public AutoCloseCacheFactory(CacheFactory cacheFactory) {
        this.cacheFactory = cacheFactory;
    }

    public CacheReference<PersistentCache> open(File cacheDir, CacheUsage usage, Map<String, ?> properties) {
        File canonicalDir = GFileUtils.canonicalise(cacheDir);
        CacheInfo cacheInfo = openCaches.get(canonicalDir);
        if (cacheInfo == null) {
            CacheReference<PersistentCache> cache = cacheFactory.open(cacheDir, usage, properties);
            cacheInfo = new CacheInfo(cache, properties);
            openCaches.put(canonicalDir, cacheInfo);
        } else {
            if (!properties.equals(cacheInfo.properties)) {
                throw new UnsupportedOperationException(String.format("Cache '%s' is already open with different state.", cacheDir));
            }
        }
        cacheInfo.addReference();
        return cacheInfo;
    }

    public void close() {
        try {
            for (CacheInfo cacheInfo : openCaches.values()) {
                cacheInfo.close();
            }
        } finally {
            openCaches.clear();
        }
    }

    private class CacheInfo implements CacheReference<PersistentCache> {
        int count;
        final Map<String, ?> properties;
        final CacheReference<PersistentCache> cache;

        private CacheInfo(CacheReference<PersistentCache> cache, Map<String, ?> properties) {
            this.cache = cache;
            this.properties = new HashMap<String, Object>(properties);
        }

        public PersistentCache getCache() {
            return cache.getCache();
        }

        public void addReference() {
            count++;
        }

        public void release() {
            count--;
            if (count == 0) {
                cache.release();
                openCaches.values().remove(this);
            }
        }

        public void close() {
            cache.release();
        }
    }
}
