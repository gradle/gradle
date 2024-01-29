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

/**
 * Allows configuring collection-based properties, namely {@link ListProperty} and {@link SetProperty}.
 *
 * @param <T>
 *
 * @since 8.7
 */
@Incubating
public interface CollectionPropertyConfigurer<T> extends ConfigurableValue.Configurer {
    /**
     * Adds an element to the property value.
     * <p>
     * Contrary to {@link #append(Object)}, if this property has no value, this operation has no effect on the value of this property.
     * </p>
     *
     * @param element The element
     * @see #append(Object) for a more convenient
     */
    void add(T element);

    /**
     * Adds an element to the property value.
     *
     * <p>The given provider will be queried when the value of this property is queried.
     * <p>
     * Contrary to {@link #append(Provider)}, if this property has no value, this operation has no effect on the value of this property.
     * </p>
     * <p>
     * Also contrary to {@link #append(Provider)}, this property will have no value when the given provider has no value.
     * </p>
     *
     * @param provider The provider of an element
     */
    void add(Provider<? extends T> provider);

    /**
     * Adds zero or more elements to the property value.
     *
     * <p>
     * Contrary to {@link #appendAll(Object[])}, if this property has no value, this operation has no effect on the value of this property.
     * </p>
     *
     * @param elements The elements to add
     */
    void addAll(T... elements);

    /**
     * Adds zero or more elements to the property value.
     *
     * <p>The given iterable will be queried when the value of this property is queried.
     *
     * <p>
     * Contrary to {@link #appendAll(Iterable)}, if this property has no value, this operation has no effect on the value of this property.
     * </p>
     *
     * @param elements The elements to add.
     */
    void addAll(Iterable<? extends T> elements);

    /**
     * Adds zero or more elements to the property value.
     *
     * <p>The given provider will be queried when the value of this property is queried.
     * <p>
     * Contrary to {@link #appendAll(Provider)}, if this property has no value, this operation has no effect on the value of this property.
     * </p>
     * <p>
     * Also contrary to {@link #appendAll(Provider)}, this property will have no value when the given provider has no value.
     * </p>
     *
     * @param provider Provider of elements
     */
    void addAll(Provider<? extends Iterable<? extends T>> provider);

    /**
     * Adds an element to the property value.
     *
     * <p>
     * When invoked on a property with no value, this method first sets the value
     * of the property to its current convention value, if set, or an empty collection.
     * </p>
     *
     * @param element The element
     * @since 8.7
     */
    @Incubating
    void append(T element);

    /**
     * Adds an element to the property value.
     *
     * <p>The given provider will be queried when the value of this property is queried.
     * This property will have no value when the given provider has no value.
     *
     * <p>
     * When invoked on a property with no value, this method first sets the value
     * of the property to its current convention value, if set, or an empty collection.
     * </p>
     * <p>Even if the given provider has no value, after this method is invoked,
     * the actual value of this property is guaranteed to be present.</p>
     *
     * @param provider The provider of an element
     * @since 8.7
     */
    @Incubating
    void append(Provider<? extends T> provider);

    /**
     * Adds zero or more elements to the property value.
     *
     * <p>
     * When invoked on a property with no value, this method first sets the value
     * of the property to its current convention value, if set, or an empty collection.
     * </p>
     *
     * @param elements The elements to add
     * @since 8.7
     */
    @Incubating
    @SuppressWarnings("unchecked")
    void appendAll(T... elements);

    /**
     * Adds zero or more elements to the property value.
     *
     * <p>The given iterable will be queried when the value of this property is queried.
     *
     * <p>
     * When invoked on a property with no value, this method first sets the value
     * of the property to its current convention value, if set, or an empty collection.
     * </p>
     *
     * @param elements The elements to add.
     * @since 8.7
     */
    @Incubating
    void appendAll(Iterable<? extends T> elements);

    /**
     * Adds zero or more elements to the property value.
     *
     * <p>The given provider will be queried when the value of this property is queried.
     * This property will have no value when the given provider has no value.
     *
     * <p>
     * When invoked on a property with no value, this method first sets the value
     * of the property to its current convention value, if set, or an empty collection.
     * </p>
     * <p>Even if the given provider has no value, after this method is invoked,
     * the actual value of this property is guaranteed to be present.</p>
     *
     * @param provider Provider of elements
     * @since 8.7
     */
    @Incubating
    void appendAll(Provider<? extends Iterable<? extends T>> provider);
}
