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

package org.gradle.api.internal.provider;

import org.gradle.api.Incubating;
import org.gradle.api.provider.HasMultipleValues;
import org.gradle.api.provider.Provider;

import java.util.Collection;

public interface CollectionPropertyInternal<T, C extends Collection<T>> extends PropertyInternal<C>, HasMultipleValues<T>, CollectionProviderInternal<T, C> {
    @Override
    Class<T> getElementType();

    /**
     * Adds an element to the property value.
     *
     * <p>
     * When invoked on a property with no value, this method first sets the value
     * of the property to its current convention value, if set, or an empty collection.
     * </p>
     *
     * @param element The element
     */
    @Incubating
    void append(T element);

    /**
     * Adds an element to the property value.
     *
     * <p>The given provider will be queried when the value of this property is queried.
     *
     * <p>
     * When invoked on a property with no value, this method first sets the value
     * of the property to its current convention value, if set, or an empty collection.
     * </p>
     * <p>Even if the given provider has no value, after this method is invoked,
     * the actual value of this property is guaranteed to be present.</p>
     *
     * @param provider The provider of an element
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
     */
    @Incubating
    @SuppressWarnings("unchecked")
    // TODO Use @SafeVarargs and make method final
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
     */
    @Incubating
    void appendAll(Iterable<? extends T> elements);

    /**
     * Adds zero or more elements to the property value.
     *
     * <p>The given provider will be queried when the value of this property is queried.
     *
     * <p>
     * When invoked on a property with no value, this method first sets the value
     * of the property to its current convention value, if set, or an empty collection.
     * </p>
     * <p>Even if the given provider has no value, after this method is invoked,
     * the actual value of this property is guaranteed to be present.</p>
     *
     * @param provider Provider of elements
     */
    @Incubating
    void appendAll(Provider<? extends Iterable<? extends T>> provider);
}
