/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.api.internal.attributes;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * A concurrent cache that bypasses the equals and hashcode methods of the key.
 * Should be used only with keys that are known to be interned.
 * <p>
 * This is likely a more performant alternative to {@code Collections.synchronizedMap(new IdentityHashMap<>())}
 * in concurrent scenarios.
 */
class ConcurrentIdentityCache<K, V> {
    private final Map<IdentityKey<K>, V> map = new ConcurrentHashMap<>();

    public V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction) {

        IdentityKey<K> wrappedKey = new IdentityKey<>(key);

        // First attempt to fetch the value from the map without locking
        V result = map.get(wrappedKey);
        if (result != null) {
            return result;
        }

        // If it is not found, compute the value atomically
        return map.computeIfAbsent(wrappedKey, k -> mappingFunction.apply(k.value));
    }

    private static class IdentityKey<T> {

        private final T value;

        private IdentityKey(T value) {
            this.value = value;
        }

        @Override
        @SuppressWarnings({"EqualsWhichDoesntCheckParameterClass", "EqualsUnsafeCast"})
        public boolean equals(Object obj) {
            return ((IdentityKey<?>) obj).value == value;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(value);
        }

    }
}
