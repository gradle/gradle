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

package org.gradle.api.provider;

import org.gradle.api.Incubating;
import org.gradle.api.specs.Spec;

import java.util.Collection;
import java.util.Map;

/**
 * Allows configuring a {@link MapProperty}.
 *
 * @param <K>
 * @param <V>
 * @since 8.6
 */
@Incubating
public interface MapPropertyConfigurer<K, V> {
    /**
     * Adds a map entry to the property value.
     *
     * @param key the key
     * @param value the value
     */
    void put(K key, V value);

    /**
     * Adds a map entry to the property value.
     *
     * <p>The given provider will be queried when the value of this property is queried.
     * This property will have no value when the given provider has no value.
     *
     * @param key the key
     * @param providerOfValue the provider of the value
     */
    void put(K key, Provider<? extends V> providerOfValue);

    /**
     * Adds all entries from another {@link Map} to the property value.
     *
     * @param entries a {@link Map} containing the entries to add
     */
    void putAll(Map<? extends K, ? extends V> entries);

    /**
     * Adds all entries from another {@link Map} to the property value.
     *
     * <p>The given provider will be queried when the value of this property is queried.
     * This property will have no value when the given provider has no value.
     *
     * @param provider the provider of the entries
     */
    void putAll(Provider<? extends Map<? extends K, ? extends V>> provider);

    /**
     * Removes the map entry that has the given value as a key, if it exists.
     *
     * If no such entry is found, this method does nothing.
     *
     * @param key key of the entry to remove
     */
    void remove(K key);

    /**
     * Removes all map entries that are matched by the given key filter, if any.
     *
     * @param keyFilter a filter that matches keys of entries to be removed
     */
    void removeIf(Spec<K> keyFilter);

    /**
     * Removes all map entries that have any of the given keys, if any.
     *
     * @param key keys of entries to be removed
     */
    void removeAll(K... key);

    /**
     * Removes all map entries that have any of the keys provided by the given provider.
     *
     * @param keyProvider a provider of a collection of keys for entries to be removed
     */
    void removeAll(Provider<? extends Collection<? extends K>> keyProvider);
}
