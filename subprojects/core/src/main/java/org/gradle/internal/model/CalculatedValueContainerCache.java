/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.internal.model;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Maps;
import org.gradle.api.NonNullApi;
import org.gradle.internal.DisplayName;

import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * This class manages the coordination between two or more threads that are trying to calculate the same value
 * concurrently. It ensures that only one value container will be used for concurrent calculations for the same key,
 * and that the value is only calculated once for concurrent calls.  Note that this only applies to concurrent
 * calculations of the same value.  If two threads are trying to calculate the values serially, then each will
 * get their own value container.  In other words, this class only ensures that concurrent threads always get
 * the same value container, not that the same value container will be used every time
 * {@link #getReference(DisplayName, Supplier)} is called for a given key.
 *
 * Also note that this class is not appropriate for concurrent threads providing different suppliers for the same key.
 * In that case, the first supplier to be called will be used when calculating the value and then other threads will
 * get the same value container with the cached value from the first supplier.  In other words, their suppliers will
 * never be called because the value will already be cached.
 *
 * @param <T> the type to use for the uniquely identifiable key
 * @param <V> the type of the value that the value containers will hold
 */
@NonNullApi
public class CalculatedValueContainerCache<T extends DisplayName, V> {
    @VisibleForTesting
    final Map<T, Entry<V>> cache = Maps.newHashMap();
    private final CalculatedValueContainerFactory calculatedValueContainerFactory;

    public CalculatedValueContainerCache(CalculatedValueContainerFactory calculatedValueContainerFactory) {
        this.calculatedValueContainerFactory = calculatedValueContainerFactory;
    }

    /**
     * Returns a {@link ValueContainerReference} for the given key and supplier.  The reference can be used to later calculate the
     * value.
     */
    public ValueContainerReference getReference(T key, Supplier<? extends V> supplier) {
        return new ValueContainerReference(key, supplier);
    }

    @NonNullApi
    private static class Entry<V> {
        final CalculatedValueContainer<V, ?> container;
        int inUse = 0;

        public Entry(CalculatedValueContainer<V, ?> container) {
            this.container = container;
        }
    }

    /**
     * A reference to a value container that can be used to calculate its value such that concurrent calls to
     * {@link #finalizeAndGet()} and {@link #apply(Function)} always use the same value container for the same
     * key.  An effort is made to reuse the same container across multiple calls on the container reference
     * object, but this is not guaranteed.  There are situations where a different container may be used in
     * order to ensure that concurrent threads always use the same container.
     */
    @NonNullApi
    public class ValueContainerReference {
        private final T key;
        private final Supplier<? extends V> supplier;
        CalculatedValueContainer<V, ?> container;

        public ValueContainerReference(T key, Supplier<? extends V> supplier) {
            this.key = key;
            this.supplier = supplier;
        }

        /**
         * Finalize the value on the value container and return it.
         *
         * @return the calculated value from the container
         */
        public V finalizeAndGet() {
            return apply(container -> {
                container.finalizeIfNotAlready();
                return container.get();
            });
        }

        /**
         * Apply the given function to the value container and return the result.
         *
         * @param function the function to apply to the value container
         * @return the result of applying the function to the value container
         * @param <R> the type of the result
         */
        public <R> R apply(Function<CalculatedValueContainer<V, ?>, R> function) {
            Entry<V> entry;
            synchronized (cache) {
                if (cache.containsKey(key)) {
                    entry = cache.get(key);
                    container = entry.container;
                } else {
                    if (container == null) {
                        container = calculatedValueContainerFactory.create(key, supplier);
                    }
                    entry = new Entry<>(container);
                    cache.put(key, entry);
                }
                entry.inUse++;
            }
            try {
                return function.apply(container);
            } finally {
                synchronized (cache) {
                    entry.inUse--;
                    if (entry.inUse <= 0) {
                        cache.remove(key);
                    }
                }
            }
        }
    }
}
