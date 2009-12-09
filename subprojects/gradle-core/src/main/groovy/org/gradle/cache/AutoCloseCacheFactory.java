/*
 * Copyright 2009 the original author or authors.
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

import java.io.File;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class AutoCloseCacheFactory implements CacheFactory {
    private final CacheFactory cacheFactory;
    private final Set<PersistentCache> openCaches = new HashSet<PersistentCache>();

    public AutoCloseCacheFactory(CacheFactory cacheFactory) {
        this.cacheFactory = cacheFactory;
    }

    public PersistentCache open(File cacheDir, CacheUsage usage, Map<String, ?> properties) {
        PersistentCache cache = cacheFactory.open(cacheDir, usage, properties);
        openCaches.add(cache);
        return cache;
    }

    public void close(PersistentCache cache) {
        throw new UnsupportedOperationException();
    }

    public void close() {
        try {
            for (PersistentCache cache : openCaches) {
                cacheFactory.close(cache);
            }
        } finally {
            openCaches.clear();
        }
    }
}
