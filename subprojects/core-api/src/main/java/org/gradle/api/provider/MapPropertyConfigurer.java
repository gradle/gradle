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

import java.util.Map;

/**
 * Allows configuring a {@link MapProperty}.
 *
 * @param <K>
 * @param <V>
 * @since 8.7
 */
@Incubating
public interface MapPropertyConfigurer<K, V>  extends ConfigurableValue.Configurer {
    /**
     * Adds a map entry to the property value.
     *
     * <p>
     * Contrary to {@link #insert(Object, Object)}, if this property has no value, this operation has no effect on the value of this property.
     * </p>
     *
     * @param key the key
     * @param value the value
     */
    void put(K key, V value);

    /**
     * Adds a map entry to the property value.
     *
     * <p>The given provider will be queried when the value of this property is queried.
     * <p>
     * Contrary to {@link #insert(Object, Provider)}, if this property has no value, this operation has no effect on the value of this property.
     * </p>
     * <p>
     * Also contrary to {@link #insert(Object, Provider)}, this property will have no value when the given provider has no value.
     * </p>
     *
     * @param key the key
     * @param providerOfValue the provider of the value
     */
    void put(K key, Provider<? extends V> providerOfValue);

    /**
     * Adds all entries from another {@link Map} to the property value.
     *
     * <p>
     * Contrary to {@link #insertAll(Map)}, if this property has no value, this operation has no effect on the value of this property.
     * </p>
     *
     * @param entries a {@link Map} containing the entries to add
     */
    void putAll(Map<? extends K, ? extends V> entries);

    /**
     * Adds all entries from another {@link Map} to the property value.
     *
     * <p>The given provider will be queried when the value of this property is queried.
     * <p>
     * Contrary to {@link #insertAll(Provider)}, if this property has no value, this operation has no effect on the value of this property.
     * </p>
     * <p>
     * Also contrary to {@link #insertAll(Provider)}, this property will have no value when the given provider has no value.
     * </p>
     *
     * @param provider the provider of the entries
     */
    void putAll(Provider<? extends Map<? extends K, ? extends V>> provider);

    /**
     * Adds a map entry to the property value.
     *
     * <p>
     * When invoked on a property with no value, this method first sets the value
     * of the property to its current convention value, if set, or an empty map.
     * </p>
     *
     * @param key the key
     * @param value the value
     *
     * @since 8.7
     */
    @Incubating
    void insert(K key, V value);

    /**
     * Adds a map entry to the property value.
     *
     * <p>The given provider will be queried when the value of this property is queried.
     * <p>
     * When invoked on a property with no value, this method first sets the value
     * of the property to its current convention value, if set, or an empty map.
     * </p>
     * <p>Even if the given provider has no value, after this method is invoked,
     * the actual value of this property is guaranteed to be present.</p>
     *
     * @param key the key
     * @param providerOfValue the provider of the value
     *
     * @since 8.7
     */
    @Incubating
    void insert(K key, Provider<? extends V> providerOfValue);

    /**
     * Adds all entries from another {@link Map} to the property value.
     *
     * <p>
     * When invoked on a property with no value, this method first sets the value
     * of the property to its current convention value, if set, or an empty map.
     * </p>
     *
     * @param entries a {@link Map} containing the entries to add
     *
     * @since 8.7
     */
    @Incubating
    void insertAll(Map<? extends K, ? extends V> entries);

    /**
     * Adds all entries from another {@link Map} to the property value.
     *
     * <p>The given provider will be queried when the value of this property is queried.
     *
     * <p>
     * When invoked on a property with no value, this method first sets the value
     * of the property to its current convention value, if set, or an empty map.
     * </p>
     * <p>Even if the given provider has no value, after this method is invoked,
     * the actual value of this property is guaranteed to be present.</p>
     *
     * @param provider the provider of the entries
     *
     * @since 8.7
     */
    @Incubating
    void insertAll(Provider<? extends Map<? extends K, ? extends V>> provider);
}
