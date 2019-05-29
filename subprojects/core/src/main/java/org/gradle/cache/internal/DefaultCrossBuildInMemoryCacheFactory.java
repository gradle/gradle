/*
 * Copyright 2018 the original author or authors.
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

import javax.annotation.concurrent.ThreadSafe;
import org.gradle.api.Transformer;
import org.gradle.initialization.SessionLifecycleListener;
import org.gradle.internal.event.ListenerManager;

import javax.annotation.Nullable;
import java.lang.ref.SoftReference;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * A factory for {@link CrossBuildInMemoryCache} instances.
 *
 * Note that this implementation should only be used to create global scoped services.
 * Note that this implementation currently retains strong references to keys and values during the whole lifetime of a build session.
 *
 * Uses a simple algorithm to collect unused values, by retaining strong references to all keys and values used during the current build session, and the previous build session. All other values are referenced only by soft references.
 */
@ThreadSafe
public class DefaultCrossBuildInMemoryCacheFactory extends CrossBuildInMemoryCacheFactory {
    private final ListenerManager listenerManager;

    public DefaultCrossBuildInMemoryCacheFactory(ListenerManager listenerManager) {
        this.listenerManager = listenerManager;
    }

    /**
     * Creates a new cache instance. Keys are always referenced using strong references, values by strong or soft references depending on their usage.
     *
     * Note: this should be used to create _only_ global scoped instances.
     */
    @Override
    public <K, V> CrossBuildInMemoryCache<K, V> newCache() {
        DefaultCrossBuildInMemoryCache<K, V> cache = new DefaultCrossBuildInMemoryCache<K, V>(new HashMap<K, SoftReference<V>>());
        listenerManager.addListener(cache);
        return cache;
    }

    /**
     * Creates a new cache instance whose keys are Class instances. Keys are referenced using strong or weak references, values by strong or soft references depending on their usage.
     *
     * Note: this should be used to create _only_ global scoped instances.
     */
    @Override
    public <V> CrossBuildInMemoryCache<Class<?>, V> newClassCache() {
        DefaultCrossBuildInMemoryCache<Class<?>, V> cache = new DefaultCrossBuildInMemoryCache<Class<?>, V>(new WeakHashMap<Class<?>, SoftReference<V>>());
        listenerManager.addListener(cache);
        return cache;
    }

    private static class DefaultCrossBuildInMemoryCache<K, V> implements CrossBuildInMemoryCache<K, V>, SessionLifecycleListener {
        private final Object lock = new Object();
        private final Map<K, V> valuesForThisSession = new HashMap<K, V>();
        // This is used only to retain strong references to the values
        private final Set<V> valuesForPreviousSession = new HashSet<V>();
        private final Map<K, SoftReference<V>> allValues;

        public DefaultCrossBuildInMemoryCache(Map<K, SoftReference<V>> allValues) {
            this.allValues = allValues;
        }

        @Override
        public void afterStart() {
        }

        @Override
        public void beforeComplete() {
            synchronized (lock) {
                // Retain strong references to the values created for this session
                valuesForPreviousSession.clear();
                valuesForPreviousSession.addAll(valuesForThisSession.values());
                valuesForThisSession.clear();
            }
        }

        @Override
        public void clear() {
            synchronized (lock) {
                valuesForThisSession.clear();
                valuesForPreviousSession.clear();
                allValues.clear();
            }
        }

        @Nullable
        @Override
        public V get(K key) {
            synchronized (lock) {
                return getIfPresent(key);
            }
        }

        @Override
        public V get(K key, Transformer<V, K> factory) {
            synchronized (lock) {
                V v = getIfPresent(key);
                if (v != null) {
                    return v;
                }

                // TODO - do not hold lock while computing value
                v = factory.transform(key);

                allValues.put(key, new SoftReference<V>(v));
                // Retain strong reference
                valuesForThisSession.put(key, v);

                return v;
            }
        }

        @Override
        public void put(K key, V value) {
            synchronized (lock) {
                allValues.put(key, new SoftReference<V>(value));
                valuesForThisSession.put(key, value);
            }
        }

        // Caller must be holding lock
        private V getIfPresent(K key) {
            V v = valuesForThisSession.get(key);
            if (v != null) {
                return v;
            }

            SoftReference<V> reference = allValues.get(key);
            if (reference != null) {
                v = reference.get();
                if (v != null) {
                    // Retain strong reference
                    valuesForThisSession.put(key, v);
                    return v;
                }
            }

            return null;
        }
    }
}
