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

package org.gradle.api.provider;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.Set;

/**
 * Represents a property whose type is a {@link Map} of keys of type {@link K} and values of type {@link V}. Retains iteration order.
 *
 * <p>
 * You can create a {@link MapProperty} instance using factory method {@link org.gradle.api.model.ObjectFactory#mapProperty(Class, Class)}.
 * </p>
 *
 * <p><b>Note:</b> This interface is not intended for implementation by build script or plugin authors.
 *
 * @param <K> the type of keys.
 * @param <V> the type of values.
 * @since 5.1
 */
public interface MapProperty<K, V> extends Provider<Map<K, V>>, HasConfigurableValue {

    /**
     * Sets the value of this property to an empty map, and replaces any existing value.
     *
     * @return this property.
     */
    MapProperty<K, V> empty();

    /**
     * Returns a provider that resolves to the value of the mapping of the given key. It will have no value
     * if the property has no value, or if it does not contain a mapping for the key.
     *
     * <p>The returned provider will track the value of this property and query its value when it is queried.</p>
     *
     * <p>This method is equivalent to
     *
     * <pre><code>
     *     map(m -&gt; m.get(key))
     * </code></pre>
     *
     * but possibly more efficient.
     *
     * @param key the key
     * @return a {@link Provider} for the value
     */
    Provider<V> getting(K key);

    /**
     * Sets the value of this property to the entries of the given Map, and replaces any existing value.
     * This property will query the entries of the map each time the value of this property is queried.
     *
     * <p>This method can also be used to discard the value of the property, by passing {@code null} as the value.
     * The convention for this property, if any, will be used to provide the value instead.
     *
     * @param entries the entries, can be {@code null}
     */
    void set(@Nullable Map<? extends K, ? extends V> entries);

    /**
     * Sets the property to have the same value of the given provider, and replaces any existing value.
     *
     * This property will track the value of the provider and query its value each time the value of this property is queried.
     * When the provider has no value, this property will also have no value.
     *
     * @param provider Provider of the entries.
     */
    void set(Provider<? extends Map<? extends K, ? extends V>> provider);

    /**
     * Sets the value of this property to the entries of the given Map, and replaces any existing value.
     * This property will query the entries of the map each time the value of this property is queried.
     *
     * <p>This is the same as {@link #set(Map)} but returns this property to allow method chaining.</p>
     *
     * @param entries the entries, can be {@code null}
     * @return this
     * @since 5.6
     */
    MapProperty<K, V> value(@Nullable Map<? extends K, ? extends V> entries);

    /**
     * Sets the property to have the same value of the given provider, and replaces any existing value.
     *
     * This property will track the value of the provider and query its value each time the value of this property is queried.
     * When the provider has no value, this property will also have no value.
     *
     * <p>This is the same as {@link #set(Provider)} but returns this property to allow method chaining.</p>
     *
     * @param provider Provider of the entries.
     * @since 5.6
     */
    MapProperty<K, V> value(Provider<? extends Map<? extends K, ? extends V>> provider);

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
     * Returns a {@link Provider} that returns the set of keys for the map that is the property value.
     *
     * <p>The returned provider will track the value of this property and query its value when it is queried.</p>
     *
     * <p>This method is equivalent to
     *
     * <pre><code>
     *     map(m -&gt; m.keySet())
     * </code></pre>
     *
     * but possibly more efficient.
     *
     * @return a {@link Provider} that provides the set of keys for the map
     */
    Provider<Set<K>> keySet();

    /**
     * Specifies the value to use as the convention for this property. The convention is used when no value has been set for this property.
     *
     * @param value The value, or {@code null} when the convention is that the property has no value.
     * @return this
     */
    MapProperty<K, V> convention(@Nullable Map<? extends K, ? extends V> value);

    /**
     * Specifies the provider of the value to use as the convention for this property. The convention is used when no value has been set for this property.
     *
     * @param valueProvider The provider of the value.
     * @return this
     */
    MapProperty<K, V> convention(Provider<? extends Map<? extends K, ? extends V>> valueProvider);

    /**
     * Disallows further changes to the value of this property. Calls to methods that change the value of this property, such as {@link #set(Map)} or {@link #put(Object, Object)} will fail.
     *
     * <p>When this property has elements provided by a {@link Provider}, the value of the provider is queried when this method is called and the value of the provider will no longer be tracked.</p>
     *
     * <p>Note that although the value of the property will not change, the resulting map may contain mutable objects. Calling this method does not guarantee that the value will become immutable.</p>
     */
    @Override
    void finalizeValue();
}
