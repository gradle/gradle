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

import org.gradle.cache.ManualEvictionInMemoryCache;
import org.gradle.internal.classloader.VisitableURLClassLoader;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.session.BuildSessionLifecycleListener;

import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;
import java.lang.ref.SoftReference;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * A factory for {@link CrossBuildInMemoryCache} instances.
 *
 * Note that this implementation should only be used to create global scoped services.
 * Note that this implementation currently retains strong references to keys and values during the whole lifetime of a build session.
 *
 * Uses a simple algorithm to collect unused values, by retaining strong references to all keys and values used during the current build session, and the previous build session. All other values are referenced only by soft references.
 */
@ThreadSafe
public class DefaultCrossBuildInMemoryCacheFactory implements CrossBuildInMemoryCacheFactory {
    private final ListenerManager listenerManager;

    public DefaultCrossBuildInMemoryCacheFactory(ListenerManager listenerManager) {
        this.listenerManager = listenerManager;
    }

    @Override
    public <K, V> CrossBuildInMemoryCache<K, V> newCache() {
        DefaultCrossBuildInMemoryCache<K, V> cache = new DefaultCrossBuildInMemoryCache<>(new HashMap<>());
        listenerManager.addListener(cache);
        return cache;
    }

    @Override
    public <K, V> CrossBuildInMemoryCache<K, V> newCacheRetainingDataFromPreviousBuild(Predicate<V> retentionFilter) {
        CrossBuildCacheRetainingDataFromPreviousBuild<K, V> cache = new CrossBuildCacheRetainingDataFromPreviousBuild<>(retentionFilter);
        listenerManager.addListener(cache);
        return cache;
    }

    @Override
    public <V> CrossBuildInMemoryCache<Class<?>, V> newClassCache() {
        // Should use some variation of DefaultClassMap below to associate values with classes, as currently we retain a strong reference to each value for one session after the ClassLoader
        // for the entry's key is discarded, which is unnecessary because we won't attempt to locate the entry again once the ClassLoader has been discarded
        DefaultCrossBuildInMemoryCache<Class<?>, V> cache = new DefaultCrossBuildInMemoryCache<>(new WeakHashMap<>());
        listenerManager.addListener(cache);
        return cache;
    }

    @Override
    public <V> CrossBuildInMemoryCache<Class<?>, V> newClassMap() {
        DefaultClassMap<V> map = new DefaultClassMap<>();
        listenerManager.addListener(map);
        return map;
    }

    private abstract static class AbstractCrossBuildInMemoryCache<K, V> implements CrossBuildInMemoryCache<K, V>, BuildSessionLifecycleListener {
        private final Object lock = new Object();
        private final Map<K, V> valuesForThisSession = new HashMap<>();

        @Override
        public void beforeComplete() {
            synchronized (lock) {
                retainValuesFromCurrentSession(valuesForThisSession.values());
                valuesForThisSession.clear();
            }
        }

        @Override
        public void clear() {
            synchronized (lock) {
                valuesForThisSession.clear();
                discardRetainedValues();
            }
        }

        protected abstract void retainValuesFromCurrentSession(Collection<V> values);

        protected abstract void discardRetainedValues();

        protected abstract void retainValue(K key, V v);

        @Nullable
        protected abstract V maybeGetRetainedValue(K key);

        @Nullable
        @Override
        public V getIfPresent(K key) {
            synchronized (lock) {
                return getIfPresentWithoutLock(key);
            }
        }

        @Override
        public V get(K key, Function<? super K, ? extends V> factory) {
            synchronized (lock) {
                V v = getIfPresentWithoutLock(key);
                if (v != null) {
                    return v;
                }

                // TODO - do not hold lock while computing value
                v = factory.apply(key);

                retainValue(key, v);

                // Retain strong reference
                valuesForThisSession.put(key, v);

                return v;
            }
        }

        @Override
        public void put(K key, V value) {
            synchronized (lock) {
                retainValue(key, value);
                valuesForThisSession.put(key, value);
            }
        }

        // Caller must be holding lock
        @Nullable
        private V getIfPresentWithoutLock(K key) {
            V v = valuesForThisSession.get(key);
            if (v != null) {
                return v;
            }

            v = maybeGetRetainedValue(key);
            if (v != null) {
                // Retain strong reference
                valuesForThisSession.put(key, v);
                return v;
            }

            return null;
        }
    }

    private static class DefaultCrossBuildInMemoryCache<K, V> extends AbstractCrossBuildInMemoryCache<K, V> {
        // This is used only to retain strong references to the values
        private final Set<V> valuesForPreviousSession = new HashSet<>();
        private final Map<K, SoftReference<V>> allValues;

        public DefaultCrossBuildInMemoryCache(Map<K, SoftReference<V>> allValues) {
            this.allValues = allValues;
        }

        @Override
        protected void retainValuesFromCurrentSession(Collection<V> values) {
            // Retain strong references to the values created for this session
            valuesForPreviousSession.clear();
            valuesForPreviousSession.addAll(values);
        }

        @Override
        protected void discardRetainedValues() {
            valuesForPreviousSession.clear();
            allValues.clear();
        }

        @Override
        protected void retainValue(K key, V v) {
            allValues.put(key, new SoftReference<>(v));
        }

        @Nullable
        @Override
        protected V maybeGetRetainedValue(K key) {
            SoftReference<V> reference = allValues.get(key);
            if (reference != null) {
                return reference.get();
            }
            return null;
        }
    }

    /**
     * Retains strong references to the keys and values via the key's ClassLoader. This allows the ClassLoader to be collected.
     */
    private static class DefaultClassMap<V> extends AbstractCrossBuildInMemoryCache<Class<?>, V> {
        // Currently retains strong references to types that are not loaded using a VisitableURLClassLoader
        // This is fine for JVM types, but a problem when a custom ClassLoader is used (which should probably be deprecated instead of supported)
        private final Map<Class<?>, V> leakyValues = new HashMap<>();

        @Override
        protected void retainValuesFromCurrentSession(Collection<V> values) {
            // Ignore
        }

        @Override
        protected void discardRetainedValues() {
            throw new UnsupportedOperationException();
        }

        @Override
        protected void retainValue(Class<?> key, V v) {
            getCacheScope(key).put(key, v);
        }

        @Nullable
        @Override
        protected V maybeGetRetainedValue(Class<?> key) {
            return getCacheScope(key).get(key);
        }

        private Map<Class<?>, V> getCacheScope(Class<?> type) {
            ClassLoader classLoader = type.getClassLoader();
            if (classLoader instanceof VisitableURLClassLoader) {
                return ((VisitableURLClassLoader) classLoader).getUserData(this, HashMap::new);
            }
            return leakyValues;
        }
    }

    private static class CrossBuildCacheRetainingDataFromPreviousBuild<K, V> implements CrossBuildInMemoryCache<K, V>, BuildSessionLifecycleListener {
        private final ManualEvictionInMemoryCache<K, V> delegate = new ManualEvictionInMemoryCache<>();
        private final ConcurrentMap<K, Boolean> keysFromPreviousBuild = new ConcurrentHashMap<>();
        private final ConcurrentMap<K, Boolean> keysFromCurrentBuild = new ConcurrentHashMap<>();
        private final Predicate<V> retentionFilter;

        public CrossBuildCacheRetainingDataFromPreviousBuild(Predicate<V> retentionFilter) {
            this.retentionFilter = retentionFilter;
        }

        @Override
        public V get(K key, Function<? super K, ? extends V> factory) {
            V value = delegate.get(key, factory);
            markAccessedInCurrentBuild(key, value);
            return value;
        }

        @Override
        public V getIfPresent(K key) {
            V value = delegate.getIfPresent(key);
            markAccessedInCurrentBuild(key, value);
            return value;
        }

        @Override
        public void put(K key, V value) {
            markAccessedInCurrentBuild(key, value);
            delegate.put(key, value);
        }

        private void markAccessedInCurrentBuild(K key, @Nullable V value) {
            if (value != null && retentionFilter.test(value)) {
                keysFromCurrentBuild.put(key, Boolean.TRUE);
            }
        }

        @Override
        public void clear() {
            delegate.clear();
            keysFromCurrentBuild.clear();
            keysFromPreviousBuild.clear();
        }

        @Override
        public void beforeComplete() {
            final Set<K> keysToRetain = new HashSet<>();
            keysToRetain.addAll(keysFromPreviousBuild.keySet());
            keysToRetain.addAll(keysFromCurrentBuild.keySet());

            delegate.retainAll(keysToRetain);

            keysFromPreviousBuild.clear();
            keysFromPreviousBuild.putAll(keysFromCurrentBuild);
            keysFromCurrentBuild.clear();
        }
    }
}
