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

import java.util.Collection;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Allows configuring a {@link MapProperty}.
 *
 * @param <K>
 * @param <V>
 * @since 8.5
 */
@Incubating
public interface MapConfigurer<K, V> {
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

    void excludeAll(Predicate<K> keyFilter);

    void exclude(K key);

    void excludeAll(K... key);

    void excludeAll(Provider<? extends Collection<? extends K>> provider);
}
