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
package org.gradle.cache.internal;

import org.gradle.api.internal.Factory;
import org.gradle.cache.PersistentCache;
import org.gradle.cache.PersistentIndexedCache;

import java.io.File;

public class DefaultPersistentDirectoryStore implements PersistentCache {
    private final File dir;
    private final String displayName;

    public DefaultPersistentDirectoryStore(File dir, String displayName) {
        this.dir = dir;
        this.displayName = displayName != null ? displayName : String.format("cache directory %s", dir);
        dir.mkdirs();
    }

    public File getBaseDir() {
        return dir;
    }
    @Override
    public String toString() {
        return displayName;
    }

    public <K, V> PersistentIndexedCache<K, V> createCache(File cacheFile, Class<K> keyType, Class<V> valueType) {
        throw new UnsupportedOperationException();
    }

    public <T> T useCache(String operationDisplayName, Factory<? extends T> action) {
        throw new UnsupportedOperationException();
    }

    public <T> T longRunningOperation(String operationDisplayName, Factory<? extends T> action) {
        throw new UnsupportedOperationException();
    }

    public void longRunningOperation(String operationDisplayName, Runnable action) {
        throw new UnsupportedOperationException();
    }
}
