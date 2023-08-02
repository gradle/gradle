/*
 * Copyright 2017 the original author or authors.
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

import org.gradle.api.SupportsKotlinAssignmentOverloading;

import javax.annotation.Nullable;

/**
 * Represents a property whose value can be set using multiple elements of type {@link T}, such as a collection property.
 *
 * <p><b>Note:</b> This interface is not intended for implementation by build script or plugin authors. You can use the factory methods on {@link org.gradle.api.model.ObjectFactory} to create various subtypes of this interface.
 *
 * @param <T> the type of elements.
 * @since 4.5
 */
@SupportsKotlinAssignmentOverloading
public interface HasMultipleValues<T> extends HasConfigurableValue {
    /**
     * Sets the value of the property to the elements of the given iterable, and replaces any existing value. This property will query the elements of the iterable each time the value of this property is queried.
     *
     * <p>This method can also be used to discard the value of the property, by passing {@code null} as the value.
     * The convention for this property, if any, will be used to provide the value instead.
     *
     * @param elements The elements, can be null.
     */
    void set(@Nullable Iterable<? extends T> elements);

    /**
     * Sets the property to have the same value of the given provider, and replaces any existing value. This property will track the value of the provider and query its value each time the value of this property is queried. When the provider has no value, this property will also have no value.
     *
     * @param provider Provider of the elements.
     */
    void set(Provider<? extends Iterable<? extends T>> provider);

    /**
     * Sets the value of the property to the elements of the given iterable, and replaces any existing value. This property will query the elements of the iterable each time the value of this property is queried.
     *
     * <p>This is the same as {@link #set(Iterable)} but returns this property to allow method chaining.</p>
     *
     * @param elements The elements, can be null.
     * @return this
     * @since 5.6
     */
    HasMultipleValues<T> value(@Nullable Iterable<? extends T> elements);

    /**
     * Sets the property to have the same value of the given provider, and replaces any existing value. This property will track the value of the provider and query its value each time the value of this property is queried. When the provider has no value, this property will also have no value.
     *
     * <p>This is the same as {@link #set(Provider)} but returns this property to allow method chaining.</p>
     *
     * @param provider Provider of the elements.
     * @return this
     * @since 5.6
     */
    HasMultipleValues<T> value(Provider<? extends Iterable<? extends T>> provider);

    /**
     * Sets the value of this property to an empty collection, and replaces any existing value.
     *
     * @return this property.
     * @since 5.0
     */
    HasMultipleValues<T> empty();

    /**
     * Adds an element to the property value.
     *
     * @param element The element
     */
    void add(T element);

    /**
     * Adds an element to the property value.
     *
     * <p>The given provider will be queried when the value of this property is queried.
     * This property will have no value when the given provider has no value.
     *
     * @param provider The provider of an element
     */
    void add(Provider<? extends T> provider);

    /**
     * Adds zero or more elements to the property value.
     *
     * @param elements The elements to add
     * @since 4.10
     */
    @SuppressWarnings("unchecked")
    void addAll(T... elements);

    /**
     * Adds zero or more elements to the property value.
     *
     * <p>The given iterable will be queried when the value of this property is queried.
     *
     * @param elements The elements to add.
     * @since 4.10
     */
    void addAll(Iterable<? extends T> elements);

    /**
     * Adds zero or more elements to the property value.
     *
     * <p>The given provider will be queried when the value of this property is queried.
     * This property will have no value when the given provider has no value.
     *
     * @param provider Provider of elements
     */
    void addAll(Provider<? extends Iterable<? extends T>> provider);

    /**
     * Specifies the value to use as the convention for this property. The convention is used when no value has been set for this property.
     *
     * @param elements The elements, or {@code null} when the convention is that the property has no value.
     * @return this
     * @since 5.1
     */
    HasMultipleValues<T> convention(@Nullable Iterable<? extends T> elements);

    /**
     * Specifies the provider of the value to use as the convention for this property. The convention is used when no value has been set for this property.
     *
     * @param provider The provider of the elements
     * @return this
     * @since 5.1
     */
    HasMultipleValues<T> convention(Provider<? extends Iterable<? extends T>> provider);

    /**
     * Disallows further changes to the value of this property. Calls to methods that change the value of this property, such as {@link #set(Iterable)} or {@link #add(Object)} will fail.
     *
     * <p>When this property has elements provided by a {@link Provider}, the value of the provider is queried when this method is called  and the value of the provider will no longer be tracked.</p>
     *
     * <p>Note that although the value of the property will not change, the resulting collection may contain mutable objects. Calling this method does not guarantee that the value will become immutable.</p>
     *
     * @since 5.0
     */
    @Override
    void finalizeValue();
}
