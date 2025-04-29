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
import org.gradle.internal.UncheckedException;
import org.gradle.internal.classloader.VisitableURLClassLoader;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.lazy.Lazy;
import org.gradle.internal.session.BuildSessionLifecycleListener;
import org.jspecify.annotations.Nullable;

import javax.annotation.concurrent.ThreadSafe;
import java.lang.ref.SoftReference;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static java.util.Collections.synchronizedMap;

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
        DefaultCrossBuildInMemoryCache<K, V> cache = new DefaultCrossBuildInMemoryCache<>(KeyRetentionPolicy.STRONG);
        listenerManager.addListener(cache);
        return cache;
    }

    @Override
    public <K, V> CrossBuildInMemoryCache<K, V> newCache(Consumer<V> onReuse) {
        DefaultCrossBuildInMemoryCache<K, V> cache = new DefaultCrossBuildInMemoryCache<K, V>(KeyRetentionPolicy.STRONG) {
            @Nullable
            @Override
            protected V maybeGetRetainedValue(K key) {
                V v = super.maybeGetRetainedValue(key);
                if (v != null) {
                    // This callback better be swift as it runs under the cache lock.
                    onReuse.accept(v);
                }
                return v;
            }
        };
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
        // TODO: Should use some variation of DefaultClassMap below to associate values with classes, as currently we retain a strong reference to each value for one session after the ClassLoader
        //       for the entry's key is discarded, which is unnecessary because we won't attempt to locate the entry again once the ClassLoader has been discarded
        DefaultCrossBuildInMemoryCache<Class<?>, V> cache = new DefaultCrossBuildInMemoryCache<>(KeyRetentionPolicy.WEAK);
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
        private final ConcurrentHashMap<K, Lazy<V>> valuesForThisSession = new ConcurrentHashMap<>();

        @Override
        public void beforeComplete() {
            retainValuesFromCurrentSession(valuesForThisSession.values().stream().map(Lazy::get));
            valuesForThisSession.clear();
        }

        @Override
        public void clear() {
            valuesForThisSession.clear();
            discardRetainedValues();
        }

        protected abstract void retainValuesFromCurrentSession(Stream<V> values);

        protected abstract void discardRetainedValues();

        /**
         * Must be thread-safe.
         */
        protected abstract void retainValue(K key, V v);

        /**
         * Must be thread-safe.
         */
        @Nullable
        protected abstract V maybeGetRetainedValue(K key);

        @Nullable
        @Override
        public V getIfPresent(K key) {
            Lazy<V> present = valuesForThisSession
                .computeIfAbsent(key, k -> {
                    V retained = maybeGetRetainedValue(k);
                    return retained != null
                        ? Lazy.fixed(retained)
                        : null;
                });
            return present != null
                ? present.get()
                : null;
        }

        /**
         * Factory must be thread-safe and must not rely on thread-local state as it might
         * be executed from a different thread than the one that submitted it.
         */
        @Override
        public V get(K key, Function<? super K, ? extends V> factory) {
            return valuesForThisSession.computeIfAbsent(key, k -> {
                V retained = maybeGetRetainedValue(k);
                return retained != null
                    ? Lazy.fixed(retained)
                    : Lazy.locking().of(() -> produceAndRetain(factory, k));
            }).get();
        }

        @Override
        public void put(K key, V value) {
            // Update doesn't need to be atomic since all retained values are equivalent
            retainValue(key, value);
            valuesForThisSession.put(key, Lazy.fixed(value));
        }

        private V produceAndRetain(Function<? super K, ? extends V> factory, K k) {
            V newValue = produce(factory, k);
            retainValue(k, newValue);
            return newValue;
        }

        private V produce(Function<? super K, ? extends V> factory, K k) {
            V newValue;
            try {
                newValue = factory.apply(k);
                if (newValue == null) {
                    // Factory should never produce null
                    throw new IllegalStateException("Factory '" + factory + "' failed to produce a value for key '" + k + "'!");
                }
            } catch (Throwable e) {
                valuesForThisSession.remove(k);
                throw UncheckedException.throwAsUncheckedException(e);
            }
            return newValue;
        }
    }

    private enum KeyRetentionPolicy {
        WEAK,
        STRONG
    }

    private static class DefaultCrossBuildInMemoryCache<K, V> extends AbstractCrossBuildInMemoryCache<K, V> {

        // This is used only to retain strong references to the values
        private final Set<V> valuesForPreviousSession = new HashSet<>();
        private final Map<K, SoftReference<V>> allValues;

        public DefaultCrossBuildInMemoryCache(KeyRetentionPolicy retentionPolicy) {
            this.allValues = mapFor(retentionPolicy);
        }

        private Map<K, SoftReference<V>> mapFor(KeyRetentionPolicy retentionPolicy) {
            switch (retentionPolicy) {
                case WEAK:
                    return synchronizedMap(new WeakHashMap<>());
                case STRONG:
                    return new ConcurrentHashMap<>();
            }
            throw new IllegalArgumentException("Unknown retention policy: " + retentionPolicy);
        }

        @Override
        protected void retainValuesFromCurrentSession(Stream<V> values) {
            // Retain strong references to the values created for this session
            synchronized (valuesForPreviousSession) {
                valuesForPreviousSession.clear();
                values.forEach(valuesForPreviousSession::add);
            }
        }

        @Override
        protected void discardRetainedValues() {
            synchronized (valuesForPreviousSession) {
                valuesForPreviousSession.clear();
            }
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
        private final Map<Class<?>, V> leakyValues = new ConcurrentHashMap<>();

        @Override
        protected void retainValuesFromCurrentSession(Stream<V> values) {
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
                return ((VisitableURLClassLoader) classLoader).getUserData(this, ConcurrentHashMap::new);
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
