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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static java.util.Collections.synchronizedMap;

/**
 * A factory for {@link CrossBuildInMemoryCache} instances.
 *
 * Note that this implementation should only be used to create global scoped services.
 *
 * The general-purpose caches ({@link #newCache()}) retain strong references to all keys and values used during the
 * current and previous build session, and reference all other values only by soft references, so they may be
 * collected under memory pressure.
 *
 * The class caches ({@link #newClassCache()} and {@link #newClassMap()}) instead associate each value with its key
 * {@link Class} via {@link ClassValue}, so a value lives exactly as long as its key class and is evicted only when
 * that class is unloaded, never under memory pressure. This lets classes -- and the ClassLoaders that define them --
 * be collected as soon as they are otherwise unused, including within a single build session.
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
        return new ClassValueCache<>();
    }

    @Override
    public <V> CrossBuildInMemoryCache<Class<?>, V> newClassMap() {
        return new ClassValueCache<>();
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
     * A {@link CrossBuildInMemoryCache} of values computed per {@link Class} key, backed by {@link ClassValue} so
     * that each value is stored on its key {@code Class} itself. A value therefore lives exactly as long as its key
     * class (and the {@link ClassLoader} that defined it) is reachable, and imposes no other retention. This lets
     * classes -- and the ClassLoaders that define them -- be collected as soon as they are otherwise unused, even
     * within a single build session, without this long-lived cache pinning them (see gradle/gradle#18313).
     *
     * <p>This deliberately does <em>not</em> extend {@link AbstractCrossBuildInMemoryCache}: that base strongly
     * retains every value in its {@code valuesForThisSession} map for the whole build session, which would
     * re-introduce exactly the pin this class exists to avoid.
     *
     * <p>Invariant: a value cached under a key {@code Class} must not strongly reference any other, shorter-lived
     * class, or that class would be retained for the key's lifetime. This holds for the current consumers, which
     * cache values describing their own key class (constructors, generated subclasses, type metadata).
     */
    private static class ClassValueCache<V> implements CrossBuildInMemoryCache<Class<?>, V> {

        /**
         * Holds the cached value on the key {@code Class}. The {@code epoch} lets {@link #clear()} invalidate every
         * entry in O(1) without keeping a registry of keys -- such a registry would reference the keys and so
         * re-introduce the pin this cache exists to avoid.
         */
        private static class Slot<V> {
            volatile V value;
            volatile int epoch;
        }

        private final ClassValue<Slot<V>> slots = new ClassValue<Slot<V>>() {
            @Override
            protected Slot<V> computeValue(Class<?> type) {
                return new Slot<>();
            }
        };

        private final AtomicInteger currentEpoch = new AtomicInteger();

        @Override
        public V get(Class<?> key, Function<? super Class<?>, ? extends V> factory) {
            Slot<V> slot = slots.get(key);
            int epoch = currentEpoch.get();
            V value = slot.value;
            if (value != null && slot.epoch == epoch) {
                return value;
            }
            synchronized (slot) {
                epoch = currentEpoch.get();
                if (slot.value == null || slot.epoch != epoch) {
                    V computed = factory.apply(key);
                    if (computed == null) {
                        throw new IllegalStateException("Factory '" + factory + "' failed to produce a value for key '" + key + "'!");
                    }
                    slot.value = computed;
                    slot.epoch = epoch;
                }
                return slot.value;
            }
        }

        @Nullable
        @Override
        public V getIfPresent(Class<?> key) {
            Slot<V> slot = slots.get(key);
            return slot.epoch == currentEpoch.get() ? slot.value : null;
        }

        @Override
        public void put(Class<?> key, V value) {
            Slot<V> slot = slots.get(key);
            synchronized (slot) {
                slot.value = value;
                slot.epoch = currentEpoch.get();
            }
        }

        @Override
        public void clear() {
            currentEpoch.incrementAndGet();
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
