/*
 * Copyright 2012 the original author or authors.
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

import org.gradle.cache.CacheOpenException;
import org.gradle.cache.PersistentIndexedCache;
import org.gradle.internal.Factory;
import org.gradle.messaging.serialize.Serializer;

import java.io.File;

public class DelegateOnDemandPersistentDirectoryCache implements ReferencablePersistentCache {
    private DefaultPersistentDirectoryCache delegateCache;
    private boolean isOpen;

    public DelegateOnDemandPersistentDirectoryCache(DefaultPersistentDirectoryCache cache) {
        this.delegateCache = cache;
    }

    public DelegateOnDemandPersistentDirectoryCache open() {
        this.isOpen = true;
        return this;
    }

    public void close() {
        this.isOpen = false;
        delegateCache.close();
    }

    public FileLock getLock() {
        return delegateCache.getLock();
    }

    public <T> T useCache(final String operationDisplayName, final Factory<? extends T> action) {
        return runWithOpenedCache(new Factory<T>() {
            public T create() {
                return delegateCache.useCache(operationDisplayName, action);
            }
        });
    }

    public void useCache(final String operationDisplayName, final Runnable action) {
        runWithOpenedCache(new Factory<Void>() {
            public Void create() {
                delegateCache.useCache(operationDisplayName, action);
                return null;
            }
        });
    }

    public <T> T longRunningOperation(final String operationDisplayName, final Factory<? extends T> action) {
        return runWithOpenedCache(new Factory<T>() {
            public T create() {
                return delegateCache.longRunningOperation(operationDisplayName, action);
            }
        });
    }

    public void longRunningOperation(final String operationDisplayName, final Runnable action) {
        runWithOpenedCache(new Factory<Void>() {
            public Void create() {
                delegateCache.longRunningOperation(operationDisplayName, action);
                return null;
            }
        });
    }

    private <T> T runWithOpenedCache(Factory<T> factory) {
        if (isOpen) {
            delegateCache.open();
            try {
                return factory.create();
            } finally {
                delegateCache.close();
            }
        } else {
            throw new CacheOpenException("Cannot run operation on cache that has not been opened.");
        }
    }

    public File getBaseDir() {
        return delegateCache.getBaseDir();
    }

    public <K, V> PersistentIndexedCache<K, V> createCache(File cacheFile, Class<K> keyType, Class<V> valueType) {
        throw new UnsupportedOperationException();
    }

    public <K, V> PersistentIndexedCache<K, V> createCache(File cacheFile, Class<K> keyType, Serializer<V> valueSerializer) {
        throw new UnsupportedOperationException();
    }

    public <K, V> PersistentIndexedCache<K, V> createCache(File cacheFile, Serializer<K> keySerializer, Serializer<V> valueSerializer) {
        throw new UnsupportedOperationException();
    }

    public String toString(){
        return String.format("On Demand Cache for %s", delegateCache.toString());
    }
}
