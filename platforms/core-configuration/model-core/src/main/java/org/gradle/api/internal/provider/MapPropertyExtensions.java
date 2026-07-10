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

package org.gradle.api.internal.provider;

import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Provider;

public class MapPropertyExtensions {

    /**
     * Returns a provider that resolves to the value of the mapping of the given key. It will have no value
     * if the property has no value, or if it does not contain a mapping for the key.
     *
     * <p>Extension method to support the subscript operator in Groovy.</p>
     *
     * @param self the {@link MapProperty}
     * @param key the key
     * @return a {@link Provider} for the value
     */
    public static <K, V> Provider<V> getAt(MapProperty<K, V> self, K key) {
        return self.getting(key);
    }

    /**
     * Adds a map entry to the property value.
     *
     * <p>Extension method to support the subscript operator in Groovy.</p>
     *
     * @param self the {@link MapProperty}
     * @param key the key
     * @param value the value or a {@link Provider} of the value
     */
    @SuppressWarnings("unchecked")
    public static <K, V> void putAt(MapProperty<K, V> self, K key, Object value) {
        if (value instanceof Provider<?>) {
            self.put(key, (Provider) value);
        } else {
            self.put(key, (V) value);
        }
    }

    /**
     * Returns a provider that resolves to the value of the mapping of the given key. It will have no value
     * if the property has no value, or if it does not contain a mapping for the key.
     *
     * <p>Extension method to support the subscript operator in Groovy.</p>
     *
     * @param self the {@link MapProperty}
     * @param key the key
     * @return a {@link Provider} for the value
     */
    public static <V> Provider<V> propertyMissing(MapProperty<String, V> self, String key) {
        return self.getting(key);
    }

    /**
     * Adds a map entry to the property value.
     *
     * <p>Extension method to support the subscript operator in Groovy.</p>
     *
     * @param self the {@link MapProperty}
     * @param key the key
     * @param value the value or a {@link Provider} of the value
     */
    public static <V> void propertyMissing(MapProperty<String, V> self, String key, Object value) {
        putAt(self, key, value);
    }
}
